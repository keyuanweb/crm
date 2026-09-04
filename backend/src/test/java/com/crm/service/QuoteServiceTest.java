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
import com.crm.dto.quote.QuoteRequest;
import com.crm.entity.Customer;
import com.crm.entity.Opportunity;
import com.crm.entity.Product;
import com.crm.entity.Quote;
import com.crm.repository.CustomerMapper;
import com.crm.repository.OpportunityMapper;
import com.crm.repository.ProductMapper;
import com.crm.repository.QuoteItemMapper;
import com.crm.repository.QuoteMapper;
import com.crm.security.SecurityUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/** QuoteService 单元测试（007 T020）：算额/状态机/客户商机不匹配。 */
@ExtendWith(MockitoExtension.class)
class QuoteServiceTest {

  private QuoteMapper quoteMapper;
  private QuoteItemMapper quoteItemMapper;
  private CustomerMapper customerMapper;
  private OpportunityMapper opportunityMapper;
  private ProductMapper productMapper;
  private AuditService auditService;
  private QuoteService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeEach
  void setUp() {
    // Initialize MyBatis-Plus lambda cache for Quote entity
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, Quote.class);

    quoteMapper = mock(QuoteMapper.class);
    quoteItemMapper = mock(QuoteItemMapper.class);
    customerMapper = mock(CustomerMapper.class);
    opportunityMapper = mock(OpportunityMapper.class);
    productMapper = mock(ProductMapper.class);
    auditService = mock(AuditService.class);
    service =
        new QuoteService(
            quoteMapper,
            quoteItemMapper,
            customerMapper,
            opportunityMapper,
            productMapper,
            auditService);
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

  private Product product(Long id, long price) {
    Product p = new Product();
    p.setId(id);
    p.setName("产品" + id);
    p.setStandardPrice(price);
    p.setStatus("ACTIVE");
    return p;
  }

  private QuoteRequest.QuoteItemRequest item(Long productId, int qty, String discount) {
    QuoteRequest.QuoteItemRequest i = new QuoteRequest.QuoteItemRequest();
    i.setProductId(productId);
    i.setQuantity(qty);
    i.setDiscount(new BigDecimal(discount));
    return i;
  }

  private QuoteRequest request(Long customerId, List<QuoteRequest.QuoteItemRequest> items) {
    QuoteRequest req = new QuoteRequest();
    req.setCustomerId(customerId);
    req.setItems(items);
    return req;
  }

  private Quote quote(Long id, String status) {
    Quote q = new Quote();
    q.setId(id);
    q.setQuoteNo("Q-20260822-0001");
    q.setCustomerId(10L);
    q.setStatus(status);
    q.setVersion(0);
    return q;
  }

  @Test
  @DisplayName("创建报价：行小计与总额按 单价×数量×(1-折扣) 计算")
  void createCalculatesTotals() {
    when(customerMapper.selectById(10L)).thenReturn(customer(10L));
    when(productMapper.selectById(1L)).thenReturn(product(1L, 100000L));
    when(productMapper.selectById(2L)).thenReturn(product(2L, 200000L));
    when(quoteMapper.selectList(any())).thenReturn(List.of());
    when(quoteMapper.insert(any(Quote.class)))
        .thenAnswer(
            invocation -> {
              Quote q = invocation.getArgument(0);
              q.setId(1L);
              return 1;
            });
    when(quoteItemMapper.insert(any())).thenReturn(1);

    var resp = service.create(request(10L, List.of(item(1L, 2, "0.75"), item(2L, 1, "1"))));

    // 行1: 100000×2×0.75 = 150000；行2: 200000×1×1 = 200000；总额 350000
    assertThat(resp.getTotalAmount()).isEqualTo(350000L);
    assertThat(resp.getStatus()).isEqualTo("DRAFT");
    assertThat(resp.getQuoteNo()).startsWith("Q-2026");
    verify(quoteMapper).insert(any(Quote.class));
    String todayNo =
        "Q-"
            + java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
            + "-0001";
    verify(auditService).record("CREATE", "QUOTE", 1L, "创建报价单：" + todayNo);
  }

  @Test
  @DisplayName("创建报价：客户不存在抛出 CUSTOMER_NOT_FOUND")
  void createCustomerMissingThrows() {
    when(customerMapper.selectById(10L)).thenReturn(null);

    assertThatThrownBy(() -> service.create(request(10L, List.of(item(1L, 1, "1")))))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.CUSTOMER_NOT_FOUND);
    verify(quoteMapper, never()).insert(any());
  }

  @Test
  @DisplayName("创建报价：商机与客户不匹配抛出 OPPORTUNITY_CUSTOMER_MISMATCH")
  void createOpportunityMismatchThrows() {
    when(customerMapper.selectById(10L)).thenReturn(customer(10L));
    Opportunity opp = new Opportunity();
    opp.setId(99L);
    opp.setCustomerId(999L);
    when(opportunityMapper.selectById(99L)).thenReturn(opp);

    QuoteRequest req = request(10L, List.of(item(1L, 1, "1")));
    req.setOpportunityId(99L);

    assertThatThrownBy(() -> service.create(req))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.OPPORTUNITY_CUSTOMER_MISMATCH);
  }

  @Test
  @DisplayName("创建报价：产品不存在或停用抛出 PRODUCT_NOT_FOUND")
  void createProductMissingThrows() {
    when(customerMapper.selectById(10L)).thenReturn(customer(10L));
    when(productMapper.selectById(1L)).thenReturn(null);

    assertThatThrownBy(() -> service.create(request(10L, List.of(item(1L, 1, "1")))))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
  }

  @Test
  @DisplayName("编辑已提交报价抛出 QUOTE_INVALID_STATE")
  void updatePendingThrows() {
    when(quoteMapper.selectById(1L)).thenReturn(quote(1L, "PENDING_APPROVAL"));

    assertThatThrownBy(() -> service.update(1L, request(10L, List.of(item(1L, 1, "1")))))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.QUOTE_INVALID_STATE);
  }

  @Test
  @DisplayName("提交：草稿→待审批")
  void submitTransitions() {
    Quote updated = quote(1L, "PENDING_APPROVAL");
    // 顺序 stub：require 读 DRAFT，提交后回读 PENDING
    when(quoteMapper.selectById(1L)).thenReturn(quote(1L, "DRAFT"), updated);
    when(quoteMapper.update(org.mockito.ArgumentMatchers.isNull(), any())).thenReturn(1);

    var resp = service.submit(1L);

    assertThat(resp.getStatus()).isEqualTo("PENDING_APPROVAL");
    verify(quoteMapper).update(org.mockito.ArgumentMatchers.isNull(), any());
    verify(auditService).record("SUBMIT", "QUOTE", 1L, "提交审批：Q-20260822-0001");
  }

  @Test
  @DisplayName("审批：非待审批状态重复审批抛出 QUOTE_INVALID_STATE")
  void approveNonPendingThrows() {
    when(quoteMapper.selectById(1L)).thenReturn(quote(1L, "APPROVED"));

    assertThatThrownBy(() -> service.approve(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.QUOTE_INVALID_STATE);
  }

  @Test
  @DisplayName("拒绝：缺少意见抛出 BAD_REQUEST")
  void rejectWithoutReasonThrows() {
    assertThatThrownBy(() -> service.reject(1L, null))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.BAD_REQUEST);
  }
}
