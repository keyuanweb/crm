package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.order.PaymentRequest;
import com.crm.entity.PaymentPlan;
import com.crm.entity.PaymentRecord;
import com.crm.entity.SalesOrder;
import com.crm.repository.PaymentPlanMapper;
import com.crm.repository.PaymentRecordMapper;
import com.crm.repository.SalesOrderMapper;
import com.crm.security.SecurityUtil;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/** PaymentService 单元测试（009 T023/T028）：回款登记/超额/状态驱动/提醒标识。 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

  private SalesOrderMapper orderMapper;
  private PaymentPlanMapper planMapper;
  private PaymentRecordMapper recordMapper;
  private AuditService auditService;
  private PaymentService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  /** 纯 Mockito 测试无 Spring 上下文：注册实体 TableInfo，供 LambdaQueryWrapper 解析列名。 */
  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, PaymentPlan.class);
    TableInfoHelper.initTableInfo(assistant, PaymentRecord.class);
    TableInfoHelper.initTableInfo(assistant, com.crm.entity.SalesOrder.class);
  }

  @BeforeEach
  void setUp() {
    orderMapper = mock(SalesOrderMapper.class);
    planMapper = mock(PaymentPlanMapper.class);
    recordMapper = mock(PaymentRecordMapper.class);
    auditService = mock(AuditService.class);
    service =
        new PaymentService(
            orderMapper,
            planMapper,
            recordMapper,
            auditService,
            mock(WorkflowEventPublisher.class));
    securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
    securityUtilMock.when(SecurityUtil::currentUserId).thenReturn(1L);
  }

  @AfterEach
  void tearDown() {
    securityUtilMock.close();
  }

  private SalesOrder order(Long id) {
    SalesOrder o = new SalesOrder();
    o.setId(id);
    o.setOrderNo("SO-20260822-0001");
    o.setAmount(100000L);
    o.setStatus("PENDING");
    return o;
  }

  private PaymentPlan plan(Long id, Long orderId, long amount, String dueDate, String status) {
    PaymentPlan p = new PaymentPlan();
    p.setId(id);
    p.setOrderId(orderId);
    p.setSeqNo(1);
    p.setAmount(amount);
    p.setDueDate(LocalDate.parse(dueDate));
    p.setStatus(status);
    return p;
  }

  private PaymentRequest payment(Long planId, Long amount) {
    PaymentRequest req = new PaymentRequest();
    req.setPlanId(planId);
    req.setAmount(amount);
    req.setPaidAt(LocalDate.now());
    req.setMethod("TRANSFER");
    return req;
  }

  @Test
  @DisplayName("登记全额回款：期次→PAID，订单→PAID")
  void recordFullPayment() {
    when(orderMapper.selectById(1L)).thenReturn(order(1L));
    when(planMapper.selectById(10L)).thenReturn(plan(10L, 1L, 100000L, "2026-09-01", "PENDING"));
    when(recordMapper.selectList(any())).thenReturn(List.of()); // 该期无历史
    when(recordMapper.insert(any(PaymentRecord.class))).thenReturn(1);
    when(planMapper.updateById(any(PaymentPlan.class))).thenReturn(1);
    // 订单状态重算：查询全部期次
    when(planMapper.selectList(any()))
        .thenReturn(List.of(plan(10L, 1L, 100000L, "2026-09-01", "PAID")));

    service.recordPayment(1L, payment(10L, 100000L));

    verify(recordMapper).insert(any(PaymentRecord.class));
    verify(planMapper).updateById(any(PaymentPlan.class));
    verify(orderMapper).update(org.mockito.ArgumentMatchers.isNull(), any());
    verify(auditService).record("PAYMENT", "SALES_ORDER", 1L, "登记回款：100000（期次 1）");
  }

  @Test
  @DisplayName("超额回款抛出 PAYMENT_EXCEEDS")
  void overPaymentThrows() {
    when(orderMapper.selectById(1L)).thenReturn(order(1L));
    when(planMapper.selectById(10L)).thenReturn(plan(10L, 1L, 100000L, "2026-09-01", "PENDING"));
    PaymentRecord r1 = new PaymentRecord();
    r1.setId(1L);
    r1.setAmount(80000L);
    when(recordMapper.selectList(any())).thenReturn(List.of(r1)); // 已收 80000

    assertThatThrownBy(() -> service.recordPayment(1L, payment(10L, 30000L)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.PAYMENT_EXCEEDS);
    verify(recordMapper, never()).insert(any());
  }

  @Test
  @DisplayName("已回款期次再次登记抛出 PAYMENT_EXCEEDS")
  void paidPlanReject() {
    when(orderMapper.selectById(1L)).thenReturn(order(1L));
    when(planMapper.selectById(10L)).thenReturn(plan(10L, 1L, 100000L, "2026-09-01", "PAID"));

    assertThatThrownBy(() -> service.recordPayment(1L, payment(10L, 1000L)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.PAYMENT_EXCEEDS);
  }

  @Test
  @DisplayName("期次不属于该订单抛出 PLAN_NOT_FOUND")
  void planMismatchThrows() {
    when(orderMapper.selectById(1L)).thenReturn(order(1L));
    when(planMapper.selectById(99L)).thenReturn(plan(99L, 999L, 1000L, "2026-09-01", "PENDING"));

    assertThatThrownBy(() -> service.recordPayment(1L, payment(99L, 1000L)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.PLAN_NOT_FOUND);
  }

  @Test
  @DisplayName("台账提醒标识：逾期/临期/正常/已回款")
  void reminderStatus() {
    LocalDate today = LocalDate.now();
    PaymentPlan overdue = plan(1L, 1L, 1000L, today.minusDays(5).toString(), "PENDING");
    PaymentPlan dueSoon = plan(2L, 1L, 1000L, today.plusDays(2).toString(), "PENDING");
    PaymentPlan normal = plan(3L, 1L, 1000L, today.plusDays(30).toString(), "PENDING");
    PaymentPlan paid = plan(4L, 1L, 1000L, today.plusDays(30).toString(), "PAID");
    when(recordMapper.selectList(any())).thenReturn(List.of());

    var plans = service.buildPlans(List.of(overdue, dueSoon, normal, paid));

    assertThat(plans.get(0).getReminderStatus()).isEqualTo("OVERDUE");
    assertThat(plans.get(0).getOverdueDays()).isEqualTo(5L);
    assertThat(plans.get(1).getReminderStatus()).isEqualTo("DUE_SOON");
    assertThat(plans.get(2).getReminderStatus()).isEqualTo("NORMAL");
    assertThat(plans.get(3).getReminderStatus()).isEqualTo("PAID");
  }

  @Test
  @DisplayName("提醒汇总：逾期与临期计数")
  void reminderSummaryCounts() {
    LocalDate today = LocalDate.now();
    PaymentPlan overdue = plan(1L, 1L, 1000L, today.minusDays(2).toString(), "PENDING");
    PaymentPlan dueSoon = plan(2L, 1L, 1000L, today.plusDays(1).toString(), "PENDING");
    PaymentPlan paid = plan(3L, 1L, 1000L, today.minusDays(2).toString(), "PAID");
    when(planMapper.selectList(any())).thenReturn(List.of(overdue, dueSoon, paid));

    var summary = service.reminderSummary();

    assertThat(summary.get("overdueCount")).isEqualTo(1L);
    assertThat(summary.get("dueSoonCount")).isEqualTo(1L);
  }
}
