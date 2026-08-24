package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.common.PageResult;
import com.crm.dto.customer.CustomerResponse;
import com.crm.dto.stats.DashboardStats;
import com.crm.dto.stats.HealthDistribution;
import com.crm.dto.stats.KpiBoardResponse;
import com.crm.dto.stats.LeaderboardItem;
import com.crm.dto.suggestion.SuggestionSummary;
import com.crm.entity.SalesOpportunity;
import com.crm.repository.SalesOpportunityMapper;
import com.crm.security.JwtAuthFilter.CrmPrincipal;
import com.crm.security.SecurityUtil;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/** KpiBoardService 单元测试（023 T002）：组合输出/健康度分布/缓存。 */
class KpiBoardServiceTest {

  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, SalesOpportunity.class);
  }

  private DashboardStatsService dashboardStatsService;
  private TeamLeaderboardService leaderboardService;
  private SuggestionService suggestionService;
  private CustomerService customerService;
  private Customer360Service customer360Service;
  private SalesOpportunityMapper soMapper;
  private RedisTemplate<String, Object> redis;
  private KpiBoardService service;
  private MockedStatic<SecurityUtil> securityUtilMock;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    dashboardStatsService = mock(DashboardStatsService.class);
    leaderboardService = mock(TeamLeaderboardService.class);
    suggestionService = mock(SuggestionService.class);
    customerService = mock(CustomerService.class);
    customer360Service = mock(Customer360Service.class);
    soMapper = mock(SalesOpportunityMapper.class);
    redis = mock(RedisTemplate.class);
    ValueOperations<String, Object> ops = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(ops);
    when(ops.get("stats:kpi-board")).thenReturn(null);
    service =
        new KpiBoardService(
            dashboardStatsService,
            leaderboardService,
            suggestionService,
            customerService,
            customer360Service,
            soMapper,
            redis);
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

  @Test
  @DisplayName("组合各服务输出 KpiBoardResponse")
  void combinesServices() {
    DashboardStats stats = new DashboardStats();
    DashboardStats.Summary summary = new DashboardStats.Summary(5, 8150000L, 0.4, 20, 18, 5);
    stats.setSummary(summary);
    stats.setFunnel(new DashboardStats.Funnel(List.of(), null));
    when(dashboardStatsService.getDashboard()).thenReturn(stats);
    when(leaderboardService.leaderboard(any(), any())).thenReturn(List.of(new LeaderboardItem()));
    SuggestionSummary ss = new SuggestionSummary();
    when(suggestionService.summary()).thenReturn(ss);
    when(customerService.page(any(), any(), any(), anyLong(), anyLong()))
        .thenReturn(PageResult.of(List.of(customer(1L, "客户A"), customer(2L, "客户B")), 2, 1, 20));
    when(customer360Service.healthLevelsBatch(any())).thenReturn(Map.of(1L, "GREEN", 2L, "RED"));
    when(soMapper.selectList(any())).thenReturn(List.of(so()));

    KpiBoardResponse resp = service.getKpiBoard();

    assertThat(resp.getKpi()).isEqualTo(summary);
    assertThat(resp.getFunnel()).isNotNull();
    assertThat(resp.getLeaderboard()).hasSize(1);
    assertThat(resp.getSuggestions()).isEqualTo(ss);
    assertThat(resp.getHealthDistribution().getGreen()).isEqualTo(1);
    assertThat(resp.getHealthDistribution().getRed()).isEqualTo(1);
    assertThat(resp.getTrend()).isNotEmpty();
  }

  @Test
  @DisplayName("健康度分布：RED/YELLOW/GREEN 计数正确")
  void healthDistribution() {
    DashboardStats stats = new DashboardStats();
    stats.setSummary(new DashboardStats.Summary(0, 0, 0, 0, 0, 0));
    stats.setFunnel(new DashboardStats.Funnel(List.of(), null));
    when(dashboardStatsService.getDashboard()).thenReturn(stats);
    when(leaderboardService.leaderboard(any(), any())).thenReturn(List.of());
    when(suggestionService.summary()).thenReturn(new SuggestionSummary());
    when(customerService.page(any(), any(), any(), anyLong(), anyLong()))
        .thenReturn(
            PageResult.of(
                List.of(customer(1L, "a"), customer(2L, "b"), customer(3L, "c")), 3, 1, 20));
    when(customer360Service.healthLevelsBatch(any()))
        .thenReturn(Map.of(1L, "GREEN", 2L, "YELLOW", 3L, "RED"));
    when(soMapper.selectList(any())).thenReturn(List.of());

    KpiBoardResponse resp = service.getKpiBoard();
    HealthDistribution hd = resp.getHealthDistribution();

    assertThat(hd.getGreen()).isEqualTo(1);
    assertThat(hd.getYellow()).isEqualTo(1);
    assertThat(hd.getRed()).isEqualTo(1);
  }

  private CustomerResponse customer(Long id, String name) {
    CustomerResponse c = new CustomerResponse();
    c.setId(id);
    c.setName(name);
    return c;
  }

  private SalesOpportunity so() {
    SalesOpportunity s = new SalesOpportunity();
    s.setCreatedAt(LocalDateTime.now());
    s.setAmount(100000L);
    return s;
  }
}
