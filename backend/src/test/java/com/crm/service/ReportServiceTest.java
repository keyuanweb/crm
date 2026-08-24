package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crm.common.BusinessException;
import com.crm.common.ErrorCode;
import com.crm.dto.report.ReportQuery;
import com.crm.dto.report.ReportResult;
import com.crm.entity.Lead;
import com.crm.entity.QuoteItem;
import com.crm.entity.SalesOpportunity;
import com.crm.repository.LeadMapper;
import com.crm.repository.QuoteItemMapper;
import com.crm.repository.SalesOpportunityMapper;
import com.crm.repository.UserMapper;
import com.crm.security.JwtAuthFilter.CrmPrincipal;
import com.crm.security.SecurityUtil;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/** ReportService 单元测试（021 T003）：各维度聚合/占比/校验。 */
class ReportServiceTest {

  private SalesOpportunityMapper soMapper;
  private QuoteItemMapper quoteItemMapper;
  private LeadMapper leadMapper;
  private UserMapper userMapper;
  private ReportService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeEach
  void setUp() {
    soMapper = mock(SalesOpportunityMapper.class);
    quoteItemMapper = mock(QuoteItemMapper.class);
    leadMapper = mock(LeadMapper.class);
    userMapper = mock(UserMapper.class);
    service = new ReportService(soMapper, quoteItemMapper, leadMapper, userMapper);
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

  private SalesOpportunity so(Long createdBy, Long amount, String stage, LocalDateTime createdAt) {
    SalesOpportunity s = new SalesOpportunity();
    s.setCreatedBy(createdBy);
    s.setAmount(amount);
    s.setStage(stage);
    s.setCreatedAt(createdAt);
    return s;
  }

  private ReportQuery query(String dim, String metric) {
    ReportQuery q = new ReportQuery();
    q.setDimension(dim);
    q.setMetric(metric);
    q.setStartDate("2026-08-01");
    q.setEndDate("2026-08-31");
    return q;
  }

  @Test
  @DisplayName("按销售聚合金额：created_by 分组，按金额降序，占比正确")
  void salesAggregation() {
    when(soMapper.selectList(any()))
        .thenReturn(
            List.of(
                so(1L, 800000L, "CLOSED_WON", LocalDateTime.of(2026, 8, 10, 10, 0)),
                so(1L, 200000L, "CLOSED_WON", LocalDateTime.of(2026, 8, 15, 10, 0)),
                so(2L, 500000L, "CLOSED_WON", LocalDateTime.of(2026, 8, 20, 10, 0))));
    when(userMapper.selectBatchIds(List.of(1L, 2L)))
        .thenReturn(List.of(user(1L, "张三"), user(2L, "李四")));

    ReportResult result =
        service.query(query(ReportQuery.DIMENSION_SALES, ReportQuery.METRIC_AMOUNT));

    assertThat(result.getRows()).hasSize(2);
    assertThat(result.getRows().get(0).getDimensionValue()).isEqualTo("张三");
    assertThat(result.getRows().get(0).getAmount()).isEqualTo(1000000L);
    assertThat(result.getRows().get(0).getRatio()).isEqualTo(2d / 3d);
    assertThat(result.getRows().get(1).getDimensionValue()).isEqualTo("李四");
    assertThat(result.getTotalAmount()).isEqualTo(1500000L);
  }

  @Test
  @DisplayName("按产品聚合金额：quote_item 分组")
  void productAggregation() {
    when(quoteItemMapper.selectList(any()))
        .thenReturn(
            List.of(
                item(1L, "CRM 标准版", 600000L),
                item(1L, "CRM 标准版", 400000L),
                item(2L, "CRM 旗舰版", 500000L)));

    ReportResult result =
        service.query(query(ReportQuery.DIMENSION_PRODUCT, ReportQuery.METRIC_AMOUNT));

    assertThat(result.getRows()).hasSize(2);
    assertThat(result.getRows().get(0).getDimensionValue()).isEqualTo("CRM 标准版");
    assertThat(result.getRows().get(0).getAmount()).isEqualTo(1000000L);
  }

  @Test
  @DisplayName("按线索来源聚合数量")
  void sourceAggregation() {
    when(leadMapper.selectList(any()))
        .thenReturn(List.of(lead("REFERRAL"), lead("REFERRAL"), lead("WEBSITE")));

    ReportResult result =
        service.query(query(ReportQuery.DIMENSION_SOURCE, ReportQuery.METRIC_COUNT));

    assertThat(result.getRows()).hasSize(2);
    assertThat(result.getRows().get(0).getDimensionValue()).isEqualTo("REFERRAL");
    assertThat(result.getRows().get(0).getCount()).isEqualTo(2);
    assertThat(result.getTotalCount()).isEqualTo(3);
  }

  @Test
  @DisplayName("非法维度抛 422")
  void invalidDimensionThrows() {
    ReportQuery q = query("UNKNOWN", ReportQuery.METRIC_COUNT);

    assertThatThrownBy(() -> service.query(q))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.EXPORT_TYPE_INVALID);
  }

  private com.crm.entity.User user(Long id, String name) {
    com.crm.entity.User u = new com.crm.entity.User();
    u.setId(id);
    u.setDisplayName(name);
    return u;
  }

  private QuoteItem item(Long productId, String name, Long lineTotal) {
    QuoteItem i = new QuoteItem();
    i.setProductId(productId);
    i.setProductName(name);
    i.setLineTotal(lineTotal);
    i.setQuantity(1);
    return i;
  }

  private Lead lead(String source) {
    Lead l = new Lead();
    l.setSource(source);
    l.setCreatedAt(LocalDateTime.of(2026, 8, 10, 10, 0));
    return l;
  }
}
