package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.order.OrderRequest;
import com.crm.entity.Contract;
import com.crm.entity.Customer;
import com.crm.entity.PaymentPlan;
import com.crm.entity.SalesOrder;
import com.crm.repository.ContractMapper;
import com.crm.repository.CustomerMapper;
import com.crm.repository.PaymentPlanMapper;
import com.crm.repository.SalesOrderMapper;
import com.crm.security.SecurityUtil;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/** SalesOrderService 单元测试（009 T012/T018）：创建/合同校验/期次合计/自动一期。 */
@ExtendWith(MockitoExtension.class)
class SalesOrderServiceTest {

  private SalesOrderMapper orderMapper;
  private PaymentPlanMapper planMapper;
  private CustomerMapper customerMapper;
  private ContractMapper contractMapper;
  private PaymentService paymentService;
  private AuditService auditService;
  private SalesOrderService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeEach
  void setUp() {
    orderMapper = mock(SalesOrderMapper.class);
    planMapper = mock(PaymentPlanMapper.class);
    customerMapper = mock(CustomerMapper.class);
    contractMapper = mock(ContractMapper.class);
    paymentService = mock(PaymentService.class);
    auditService = mock(AuditService.class);
    service =
        new SalesOrderService(
            orderMapper, planMapper, customerMapper, contractMapper, paymentService, auditService);
    securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
    securityUtilMock.when(SecurityUtil::currentUserId).thenReturn(1L);
  }

  @AfterEach
  void tearDown() {
    securityUtilMock.close();
  }

  private Customer customer(Long id) {
    Customer c = new Customer();
    c.setId(id);
    c.setName("测试客户");
    return c;
  }

  private Contract effectiveContract(Long id, Long customerId, long amount) {
    Contract c = new Contract();
    c.setId(id);
    c.setContractNo("HT-20260822-0001");
    c.setCustomerId(customerId);
    c.setStatus(ContractService.STATUS_EFFECTIVE);
    c.setAmount(amount);
    return c;
  }

  private OrderRequest.PlanItemRequest plan(Long amount, String dueDate) {
    OrderRequest.PlanItemRequest p = new OrderRequest.PlanItemRequest();
    p.setAmount(amount);
    p.setDueDate(LocalDate.parse(dueDate));
    return p;
  }

  private OrderRequest request(Long customerId, Long contractId, Long amount) {
    OrderRequest req = new OrderRequest();
    req.setTitle("测试订单");
    req.setCustomerId(customerId);
    req.setContractId(contractId);
    req.setAmount(amount);
    return req;
  }

  @Test
  @DisplayName("基于已生效合同创建：金额自动带入合同金额")
  void createFromEffectiveContract() {
    when(customerMapper.selectById(10L)).thenReturn(customer(10L));
    when(contractMapper.selectById(5L)).thenReturn(effectiveContract(5L, 10L, 200000L));
    when(orderMapper.selectList(any())).thenReturn(List.of());
    final SalesOrder[] saved = new SalesOrder[1];
    when(orderMapper.insert(any(SalesOrder.class)))
        .thenAnswer(
            invocation -> {
              SalesOrder o = invocation.getArgument(0);
              o.setId(1L);
              saved[0] = o;
              return 1;
            });
    when(orderMapper.selectById(1L)).thenAnswer(invocation -> saved[0]);
    when(planMapper.insert(any(PaymentPlan.class))).thenReturn(1);
    when(paymentService.buildPlans(any())).thenReturn(List.of());
    when(paymentService.recordsOf(1L)).thenReturn(List.of());

    var resp = service.create(request(10L, 5L, null));

    assertThat(resp.getAmount()).isEqualTo(200000L);
    assertThat(resp.getOrderNo()).startsWith("SO-2026");
    assertThat(resp.getStatus()).isEqualTo("PENDING");
    verify(orderMapper).insert(any(SalesOrder.class));
    // 未提供期次 → 自动一期
    verify(planMapper).insert(any(PaymentPlan.class));
  }

  @Test
  @DisplayName("基于未生效合同创建抛出 CONTRACT_NOT_EFFECTIVE")
  void createFromNonEffectiveContractThrows() {
    when(customerMapper.selectById(10L)).thenReturn(customer(10L));
    Contract draft = effectiveContract(5L, 10L, 1000L);
    draft.setStatus(ContractService.STATUS_DRAFT);
    when(contractMapper.selectById(5L)).thenReturn(draft);

    assertThatThrownBy(() -> service.create(request(10L, 5L, null)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.CONTRACT_NOT_EFFECTIVE);
    verify(orderMapper, never()).insert(any());
  }

  @Test
  @DisplayName("期次金额合计不等于订单金额抛出 PLAN_AMOUNT_MISMATCH")
  void planAmountMismatchThrows() {
    when(customerMapper.selectById(10L)).thenReturn(customer(10L));
    when(orderMapper.insert(any(SalesOrder.class))).thenReturn(1);
    OrderRequest req = request(10L, null, 100000L);
    req.setPlans(List.of(plan(30000L, "2026-09-01"), plan(40000L, "2026-10-01")));

    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.PLAN_AMOUNT_MISMATCH);
    // 订单先插入、期次校验失败回滚（@Transactional）；断言异常即可
  }

  @Test
  @DisplayName("删除存在回款记录的订单抛出 ORDER_HAS_PAYMENTS")
  void deleteWithPaymentsThrows() {
    when(orderMapper.selectById(1L)).thenReturn(null);

    // require 返回 null → ORDER_NOT_FOUND；此处单独测有回款场景
    com.crm.entity.PaymentRecord r = new com.crm.entity.PaymentRecord();
    r.setId(1L);
    SalesOrder order = new SalesOrder();
    order.setId(1L);
    order.setOrderNo("SO-20260822-0001");
    when(orderMapper.selectById(1L)).thenReturn(order);
    when(paymentService.recordsOf(1L)).thenReturn(List.of(r));

    assertThatThrownBy(() -> service.delete(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.ORDER_HAS_PAYMENTS);
    verify(orderMapper, never()).deleteById(org.mockito.ArgumentMatchers.<Long>any());
  }
}
