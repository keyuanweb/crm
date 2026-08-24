package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.common.PageResult;
import com.crm.dto.customer.CustomerHealthBrief;
import com.crm.dto.suggestion.SmartSuggestion;
import com.crm.entity.FollowUp;
import com.crm.entity.Lead;
import com.crm.entity.SalesOpportunity;
import com.crm.repository.FollowUpMapper;
import com.crm.repository.LeadMapper;
import com.crm.repository.SalesOpportunityMapper;
import com.crm.security.JwtAuthFilter.CrmPrincipal;
import com.crm.security.SecurityUtil;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

/** SuggestionService 单元测试（022 T002）：规则/排序/去重/忽略/上限。 */
class SuggestionServiceTest {

  private CustomerService customerService;
  private FollowUpMapper followUpMapper;
  private SalesOpportunityMapper soMapper;
  private LeadMapper leadMapper;
  private RedisTemplate<String, Object> redis;
  private SetOperations<String, Object> setOps;
  private SuggestionService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, FollowUp.class);
    TableInfoHelper.initTableInfo(assistant, SalesOpportunity.class);
    TableInfoHelper.initTableInfo(assistant, Lead.class);
  }

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    customerService = mock(CustomerService.class);
    followUpMapper = mock(FollowUpMapper.class);
    soMapper = mock(SalesOpportunityMapper.class);
    leadMapper = mock(LeadMapper.class);
    redis = mock(RedisTemplate.class);
    setOps = mock(SetOperations.class);
    when(redis.opsForSet()).thenReturn(setOps);
    when(setOps.members(anyString())).thenReturn(Set.of());
    service = new SuggestionService(customerService, followUpMapper, soMapper, leadMapper, redis);
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

  private CustomerHealthBrief atRisk(Long id, String name, int score) {
    CustomerHealthBrief b = new CustomerHealthBrief();
    b.setId(id);
    b.setName(name);
    b.setHealthScore(score);
    b.setDaysInactive(60);
    return b;
  }

  private SalesOpportunity stalled(Long id, String stage, LocalDateTime updatedAt) {
    SalesOpportunity s = new SalesOpportunity();
    s.setId(id);
    s.setOpportunityId(id);
    s.setStage(stage);
    s.setUpdatedAt(updatedAt);
    return s;
  }

  private FollowUp followUp(Long customerId, LocalDateTime createdAt) {
    FollowUp f = new FollowUp();
    f.setCustomerId(customerId);
    f.setCreatedAt(createdAt);
    return f;
  }

  private Lead highScoreLead(Long id, String name, int score) {
    Lead l = new Lead();
    l.setId(id);
    l.setName(name);
    l.setScore(score);
    l.setStatus("NEW");
    return l;
  }

  @Test
  @DisplayName("四类建议聚合且优先级排序（流失>停滞>待跟进>高分线索）")
  void aggregatesAndSorts() {
    when(customerService.atRiskCustomers(anyInt(), anyLong(), anyLong()))
        .thenReturn(PageResult.of(List.of(atRisk(3L, "流失客户", 35)), 1, 1, 20));
    when(soMapper.selectList(any()))
        .thenReturn(List.of(stalled(10L, "NEGOTIATING", LocalDateTime.now().minusDays(10))));
    when(followUpMapper.selectList(any()))
        .thenReturn(
            List.of(
                followUp(5L, LocalDateTime.now().minusDays(20)),
                followUp(3L, LocalDateTime.now().minusDays(50))));
    when(leadMapper.selectList(any())).thenReturn(List.of(highScoreLead(7L, "高分线索", 85)));

    List<SmartSuggestion> items = service.suggestions(20);

    assertThat(items).isNotEmpty();
    assertThat(items.get(0).getType()).isEqualTo(SmartSuggestion.TYPE_AT_RISK);
    assertThat(items.get(items.size() - 1).getType())
        .isEqualTo(SmartSuggestion.TYPE_HIGH_SCORE_LEAD);
  }

  @Test
  @DisplayName("同客户多条规则去重：取最高优先级")
  void deduplicatesSameEntity() {
    when(customerService.atRiskCustomers(anyInt(), anyLong(), anyLong()))
        .thenReturn(PageResult.of(List.of(atRisk(3L, "流失客户", 35)), 1, 1, 20));
    when(soMapper.selectList(any())).thenReturn(List.of());
    // 客户 3 同时命中待跟进（最近跟进 50 天，超出 45 上限不命中待跟进；此处验证不重复产生待跟进）
    when(followUpMapper.selectList(any()))
        .thenReturn(List.of(followUp(3L, LocalDateTime.now().minusDays(50))));
    when(leadMapper.selectList(any())).thenReturn(List.of());

    List<SmartSuggestion> items = service.suggestions(20);

    long atRiskCount =
        items.stream().filter(s -> s.getType().equals(SmartSuggestion.TYPE_AT_RISK)).count();
    long followUpCount =
        items.stream().filter(s -> s.getType().equals(SmartSuggestion.TYPE_FOLLOWUP)).count();
    assertThat(atRiskCount).isEqualTo(1);
    assertThat(followUpCount).isZero();
  }

  @Test
  @DisplayName("忽略的实体不出现")
  void ignoresExcluded() {
    when(setOps.members(anyString())).thenReturn(Set.of("CUSTOMER:3"));
    when(customerService.atRiskCustomers(anyInt(), anyLong(), anyLong()))
        .thenReturn(PageResult.of(List.of(atRisk(3L, "流失客户", 35)), 1, 1, 20));
    when(soMapper.selectList(any())).thenReturn(List.of());
    when(followUpMapper.selectList(any())).thenReturn(List.of());
    when(leadMapper.selectList(any())).thenReturn(List.of());

    List<SmartSuggestion> items = service.suggestions(20);

    assertThat(items).isEmpty();
  }

  @Test
  @DisplayName("上限截断")
  void limitTruncates() {
    when(customerService.atRiskCustomers(anyInt(), anyLong(), anyLong()))
        .thenReturn(PageResult.of(List.of(), 0, 1, 20));
    when(soMapper.selectList(any())).thenReturn(List.of());
    when(followUpMapper.selectList(any())).thenReturn(List.of());
    when(leadMapper.selectList(any()))
        .thenReturn(
            List.of(
                highScoreLead(1L, "a", 90),
                highScoreLead(2L, "b", 85),
                highScoreLead(3L, "c", 80),
                highScoreLead(4L, "d", 75)));

    List<SmartSuggestion> items = service.suggestions(2);

    assertThat(items.size()).isLessThanOrEqualTo(2);
  }
}
