package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.common.BusinessException;
import com.crm.dto.invoice.InvoiceRequest;
import com.crm.dto.invoice.InvoiceResponse;
import com.crm.dto.invoice.InvoiceStatsResponse;
import com.crm.entity.Invoice;
import com.crm.entity.SalesOrder;
import com.crm.repository.CustomerMapper;
import com.crm.repository.InvoiceMapper;
import com.crm.repository.SalesOrderMapper;
import com.crm.security.JwtAuthFilter.CrmPrincipal;
import com.crm.security.SecurityUtil;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** InvoiceService 单元测试（038 T004）。 */
class InvoiceServiceTest {

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, Invoice.class);
    TableInfoHelper.initTableInfo(assistant, SalesOrder.class);
  }

  private InvoiceMapper invoiceMapper;
  private SalesOrderMapper orderMapper;
  private CustomerMapper customerMapper;
  private AuditService auditService;
  private InvoiceService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeEach
  void setUp() {
    invoiceMapper = mock(InvoiceMapper.class);
    orderMapper = mock(SalesOrderMapper.class);
    customerMapper = mock(CustomerMapper.class);
    auditService = mock(AuditService.class);
    service = new InvoiceService(invoiceMapper, orderMapper, customerMapper, auditService);
    securityUtilMock = Mockito.mockStatic(SecurityUtil.class);
    securityUtilMock.when(SecurityUtil::currentUserId).thenReturn(1L);
    securityUtilMock
        .when(SecurityUtil::currentPrincipal)
        .thenReturn(new CrmPrincipal(1L, "admin", "ADMIN"));
  }

  @AfterEach
  void tearDown() {
    securityUtilMock.close();
  }

  private SalesOrder order(Long id, long amount) {
    SalesOrder o = new SalesOrder();
    o.setId(id);
    o.setOrderNo("DD-001");
    o.setAmount(amount);
    o.setCustomerId(1L);
    return o;
  }

  @Test
  @DisplayName("开票成功：编号生成 + ISSUED")
  void createSuccess() {
    when(orderMapper.selectById(5L)).thenReturn(order(5L, 500000L));
    when(invoiceMapper.selectList(any())).thenReturn(List.of());
    Invoice saved = new Invoice();
    saved.setId(1L);
    saved.setOrderId(5L);
    saved.setInvoiceNo("INV-202608-001");
    saved.setTitle("Acme 科技");
    saved.setAmount(100000L);
    saved.setInvoiceType("SPECIAL");
    saved.setStatus("ISSUED");
    when(invoiceMapper.selectById(1L)).thenReturn(saved);
    org.mockito.Mockito.doAnswer(
            (org.mockito.invocation.InvocationOnMock inv) -> {
              Invoice i = inv.getArgument(0);
              i.setId(1L);
              return 1;
            })
        .when(invoiceMapper)
        .insert(any(Invoice.class));

    InvoiceRequest req = new InvoiceRequest();
    req.setOrderId(5L);
    req.setTitle("Acme 科技");
    req.setAmount(100000L);
    req.setInvoiceType("SPECIAL");

    InvoiceResponse resp = service.create(req);

    assertThat(resp.getInvoiceNo()).startsWith("INV-");
    verify(invoiceMapper).insert(any(Invoice.class));
  }

  @Test
  @DisplayName("超可开额拒绝")
  void createOverLimitRejected() {
    when(orderMapper.selectById(5L)).thenReturn(order(5L, 100000L));
    Invoice issued = new Invoice();
    issued.setAmount(80000L);
    when(invoiceMapper.selectList(any())).thenReturn(List.of(issued)); // 已开 8 万

    InvoiceRequest req = new InvoiceRequest();
    req.setOrderId(5L);
    req.setTitle("Acme");
    req.setAmount(30000L); // 剩余 2 万

    assertThatThrownBy(() -> service.create(req)).isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("作废：原因必填")
  void voidRequiresReason() {
    Invoice invoice = new Invoice();
    invoice.setId(1L);
    invoice.setStatus("ISSUED");
    when(invoiceMapper.selectById(1L)).thenReturn(invoice);

    assertThatThrownBy(() -> service.voidInvoice(1L, null)).isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("作废成功：状态 VOID + 原因")
  void voidSuccess() {
    Invoice invoice = new Invoice();
    invoice.setId(1L);
    invoice.setStatus("ISSUED");
    when(invoiceMapper.selectById(1L)).thenReturn(invoice);

    InvoiceResponse resp = service.voidInvoice(1L, "开票信息错误");

    assertThat(resp.getStatus()).isEqualTo("VOID");
    assertThat(resp.getVoidReason()).isEqualTo("开票信息错误");
    verify(auditService)
        .record(Mockito.eq("VOID"), Mockito.eq("INVOICE"), Mockito.eq(1L), Mockito.anyString());
  }

  @Test
  @DisplayName("统计：开票率计算")
  void statsCalculatesRate() {
    SalesOrder o1 = order(1L, 100000L);
    SalesOrder o2 = order(2L, 100000L);
    when(orderMapper.selectList(any())).thenReturn(List.of(o1, o2));
    Invoice i1 = new Invoice();
    i1.setOrderId(1L);
    i1.setAmount(50000L);
    when(invoiceMapper.selectList(any())).thenReturn(List.of(i1));

    InvoiceStatsResponse stats = service.stats();

    assertThat(stats.getTotalOrderAmount()).isEqualTo(200000L);
    assertThat(stats.getTotalInvoiceAmount()).isEqualTo(50000L);
    assertThat(stats.getInvoiceRate()).isEqualTo(25.0);
  }
}
