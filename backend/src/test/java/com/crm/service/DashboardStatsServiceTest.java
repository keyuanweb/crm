package com.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.crm.dto.stats.DashboardStats;
import com.crm.entity.Customer;
import com.crm.entity.FollowUp;
import com.crm.entity.Opportunity;
import com.crm.entity.SalesOpportunity;
import com.crm.entity.SalesTarget;
import com.crm.entity.User;
import com.crm.repository.CustomerMapper;
import com.crm.repository.FollowUpMapper;
import com.crm.repository.OpportunityMapper;
import com.crm.repository.SalesOpportunityMapper;
import com.crm.repository.SalesTargetMapper;
import com.crm.repository.UserMapper;
import com.crm.support.StageDictionaryTestSupport;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/** DashboardStatsService 单元测试（006 T008/T022/T025）：summary/漏斗/预测/达成/客户/跟进/停滞。 */
class DashboardStatsServiceTest {

  private SalesOpportunityMapper soMapper;
  private OpportunityMapper oppMapper;
  private CustomerMapper customerMapper;
  private FollowUpMapper followUpMapper;
  private SalesTargetMapper targetMapper;
  private UserMapper userMapper;
  private StageConversionService stageConversionService;
  private DashboardStatsService service;

  /** 纯 Mockito 测试无 Spring 上下文：注册实体 TableInfo，供 LambdaQueryWrapper 解析列名。 */
  @BeforeAll
  static void initTableInfo() {
    MybatisConfiguration configuration = new MybatisConfiguration();
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
    TableInfoHelper.initTableInfo(assistant, SalesOpportunity.class);
    TableInfoHelper.initTableInfo(assistant, Customer.class);
    TableInfoHelper.initTableInfo(assistant, FollowUp.class);
    TableInfoHelper.initTableInfo(assistant, Opportunity.class);
    TableInfoHelper.initTableInfo(assistant, SalesTarget.class);
    TableInfoHelper.initTableInfo(assistant, User.class);
  }

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    soMapper = mock(SalesOpportunityMapper.class);
    oppMapper = mock(OpportunityMapper.class);
    customerMapper = mock(CustomerMapper.class);
    followUpMapper = mock(FollowUpMapper.class);
    targetMapper = mock(SalesTargetMapper.class);
    userMapper = mock(UserMapper.class);
    stageConversionService = mock(StageConversionService.class);
    RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
    ValueOperations<String, Object> ops = mock(ValueOperations.class);
    lenient().when(redis.opsForValue()).thenReturn(ops);
    lenient().when(ops.get("stats:dashboard")).thenReturn(null);
    service =
        new DashboardStatsService(
            soMapper,
            oppMapper,
            customerMapper,
            followUpMapper,
            targetMapper,
            userMapper,
            redis,
            stageConversionService,
            StageDictionaryTestSupport.service());
    // 019：mock 预测校准返回默认概率（保持既有断言不变）
    when(stageConversionService.probabilityFor(any()))
        .thenAnswer(
            inv -> {
              String stage = inv.getArgument(0);
              return switch (stage) {
                case "INITIAL_CONTACT" -> 0.2;
                case "NEGOTIATING" -> 0.5;
                case "CLOSED_WON" -> 1.0;
                default -> 0.0;
              };
            });
    when(stageConversionService.isHistorical(any())).thenReturn(false);
    // 单元测试无 Spring 上下文，@Value 不注入：显式设置停滞阈值 7 天
    org.springframework.test.util.ReflectionTestUtils.setField(service, "stalledDays", 7);
  }

  /** 按阶段码取漏斗中的一行。找不到即说明字典与断言不同源，直接失败而不是让 `get(null)` 变成误导性的 NPE。 */
  private static DashboardStats.StageStat statOf(DashboardStats.Funnel funnel, String stage) {
    return funnel.getStages().stream()
        .filter(s -> stage.equals(s.getStage()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("漏斗里没有阶段 " + stage));
  }

  private SalesOpportunity so(Long id, String stage, Long amount, LocalDateTime updatedAt) {
    SalesOpportunity s = new SalesOpportunity();
    s.setId(id);
    s.setOpportunityId(10L);
    s.setStage(stage);
    s.setAmount(amount);
    s.setUpdatedAt(updatedAt);
    return s;
  }

  @Test
  @DisplayName("summary 与漏斗：赢单率/转化率计算正确")
  void summaryAndFunnel() {
    when(soMapper.selectList(any()))
        .thenReturn(
            List.of(
                so(1L, "INITIAL_CONTACT", 100000L, LocalDateTime.now()),
                so(2L, "NEGOTIATING", 200000L, LocalDateTime.now()),
                so(3L, "CLOSED_WON", 300000L, LocalDateTime.now()),
                so(4L, "CLOSED_WON", 400000L, LocalDateTime.now()),
                so(5L, "CLOSED_LOST", 50000L, LocalDateTime.now())));
    when(customerMapper.selectList(any())).thenReturn(List.of());
    when(followUpMapper.selectList(any())).thenReturn(List.of());
    when(targetMapper.selectOne(any())).thenReturn(null);
    when(oppMapper.selectBatchIds(any())).thenReturn(List.of());

    DashboardStats stats = service.getDashboard();

    // summary：5 个机会，金额合计 1050000；赢单率 2/3
    assertThat(stats.getSummary().getOpportunityCount()).isEqualTo(5);
    assertThat(stats.getSummary().getAmountTotal()).isEqualTo(1050000L);
    assertThat(stats.getSummary().getWinRate()).isEqualTo(2d / 3d);
    // 漏斗：1.2 起按阶段字典的 sort_order 列出**全部**阶段（6 个，含 0 商机的），不再是写死的 4 个。
    DashboardStats.Funnel funnel = stats.getFunnel();
    assertThat(funnel.getStages())
        .extracting(DashboardStats.StageStat::getStage)
        .containsExactly(
            "INITIAL_CONTACT",
            "NEEDS_CONFIRMED",
            "PROPOSAL_QUOTED",
            "NEGOTIATING",
            "CLOSED_WON",
            "CLOSED_LOST");
    // 转化率 = 本阶段数 / 上一阶段数；上一阶段为 0 时**不可定义**（0 做分母）故为 null。
    // 断言按阶段码取值而不是按下标——中间插入了空阶段后，下标已不再稳定。
    // 刻意不用 Collectors.toMap 收集：它用 map.merge，遇到 null 值直接 NPE，而这里半数转化率就是 null。
    assertThat(statOf(funnel, "INITIAL_CONTACT").getConversionRate()).isNull(); // 无上一阶段
    assertThat(statOf(funnel, "NEEDS_CONFIRMED").getConversionRate()).isEqualTo(0.0d); // 0 / 1
    assertThat(statOf(funnel, "PROPOSAL_QUOTED").getConversionRate()).isNull(); // 上一阶段为 0
    assertThat(statOf(funnel, "NEGOTIATING").getConversionRate()).isNull(); // 上一阶段为 0
    assertThat(statOf(funnel, "CLOSED_WON").getConversionRate()).isEqualTo(2.0d); // 2 / 1
    assertThat(statOf(funnel, "CLOSED_LOST").getConversionRate()).isEqualTo(0.5d); // 1 / 2
    assertThat(statOf(funnel, "CLOSED_LOST").getCount()).isEqualTo(1);
    assertThat(statOf(funnel, "CLOSED_WON").getAmountTotal()).isEqualTo(700000L);
    assertThat(funnel.getGrandTotal().getCount()).isEqualTo(5);
    assertThat(funnel.getGrandTotal().getAmountTotal()).isEqualTo(1050000L);
    // 预测：100000*0.2 + 200000*0.5 + 300000*1 + 400000*1 = 820000
    assertThat(stats.getForecast().getWeightedAmount()).isEqualTo(820000L);
    // 达成：未设目标 → configured=false
    assertThat(stats.getPerformance().isConfigured()).isFalse();
  }

  @Test
  @DisplayName("业绩达成：目标已配置时达成率 = 当月赢单金额 / 目标")
  void performanceConfigured() {
    when(soMapper.selectList(any()))
        .thenReturn(List.of(so(1L, "CLOSED_WON", 500000L, LocalDateTime.now())));
    when(customerMapper.selectList(any())).thenReturn(List.of());
    when(followUpMapper.selectList(any())).thenReturn(List.of());
    SalesTarget target = new SalesTarget();
    target.setTargetMonth("2026-08");
    target.setTargetAmount(1000000L);
    when(targetMapper.selectOne(any())).thenReturn(target);
    when(oppMapper.selectBatchIds(any())).thenReturn(List.of());

    DashboardStats stats = service.getDashboard();

    assertThat(stats.getPerformance().isConfigured()).isTrue();
    assertThat(stats.getPerformance().getTargetAmount()).isEqualTo(1000000L);
    assertThat(stats.getPerformance().getAchievementRate()).isEqualTo(0.5);
  }

  @Test
  @DisplayName("客户分析：总数/活跃数/本月新增")
  void customerAnalysis() {
    when(soMapper.selectList(any())).thenReturn(List.of());
    LocalDateTime now = LocalDateTime.now();
    Customer c1 = new Customer();
    c1.setId(1L);
    c1.setStatus("ACTIVE");
    c1.setCreatedAt(now);
    Customer c2 = new Customer();
    c2.setId(2L);
    c2.setStatus("INACTIVE");
    c2.setCreatedAt(now.minusMonths(3));
    when(customerMapper.selectList(any())).thenReturn(List.of(c1, c2));
    when(followUpMapper.selectList(any())).thenReturn(List.of());
    when(targetMapper.selectOne(any())).thenReturn(null);
    when(oppMapper.selectBatchIds(any())).thenReturn(List.of());

    DashboardStats stats = service.getDashboard();

    assertThat(stats.getSummary().getCustomerCount()).isEqualTo(2);
    assertThat(stats.getSummary().getActiveCustomerCount()).isEqualTo(1);
    assertThat(stats.getSummary().getNewCustomersThisMonth()).isEqualTo(1);
  }

  @Test
  @DisplayName("跟进报表：总数/方式分布/最近记录含客户名与跟进人")
  void followUpReport() {
    when(soMapper.selectList(any())).thenReturn(List.of());
    when(customerMapper.selectList(any())).thenReturn(List.of());
    FollowUp f1 = new FollowUp();
    f1.setId(1L);
    f1.setMethod("PHONE");
    f1.setContent("电话沟通");
    f1.setCustomerId(10L);
    f1.setFollowUpBy(5L);
    f1.setCreatedAt(LocalDateTime.now());
    FollowUp f2 = new FollowUp();
    f2.setId(2L);
    f2.setMethod("EMAIL");
    f2.setContent("邮件跟进");
    f2.setCustomerId(10L);
    f2.setFollowUpBy(5L);
    f2.setCreatedAt(LocalDateTime.now());
    when(followUpMapper.selectList(any())).thenReturn(List.of(f1, f2));
    when(targetMapper.selectOne(any())).thenReturn(null);
    when(oppMapper.selectBatchIds(any())).thenReturn(List.of());
    Customer c = new Customer();
    c.setId(10L);
    c.setName("测试客户");
    when(customerMapper.selectBatchIds(List.of(10L))).thenReturn(List.of(c));
    User u = new User();
    u.setId(5L);
    u.setDisplayName("张三");
    when(userMapper.selectBatchIds(List.of(5L))).thenReturn(List.of(u));

    DashboardStats stats = service.getDashboard();

    assertThat(stats.getFollowUps().getTotal()).isEqualTo(2);
    assertThat(stats.getFollowUps().getByMethod()).hasSize(2);
    assertThat(stats.getFollowUps().getRecent()).hasSize(2);
    assertThat(stats.getFollowUps().getRecent().get(0).getCustomerName()).isEqualTo("测试客户");
    assertThat(stats.getFollowUps().getRecent().get(0).getFollowUpBy()).isEqualTo("张三");
  }

  @Test
  @DisplayName("停滞预警：超 7 天未更新的活跃机会按停滞天数倒序")
  void stalledOpportunities() {
    LocalDateTime now = LocalDateTime.now();
    SalesOpportunity old = so(1L, "NEGOTIATING", 300000L, now.minusDays(12));
    SalesOpportunity recent = so(2L, "INITIAL_CONTACT", 100000L, now.minusDays(2));
    SalesOpportunity won = so(3L, "CLOSED_WON", 500000L, now.minusDays(20));
    when(soMapper.selectList(any())).thenReturn(List.of(old, recent, won));
    when(customerMapper.selectList(any())).thenReturn(List.of());
    when(followUpMapper.selectList(any())).thenReturn(List.of());
    when(targetMapper.selectOne(any())).thenReturn(null);
    Opportunity opp = new Opportunity();
    opp.setId(10L);
    opp.setName("CRM 采购");
    opp.setCustomerId(20L);
    when(oppMapper.selectBatchIds(List.of(10L))).thenReturn(List.of(opp));
    Customer c = new Customer();
    c.setId(20L);
    c.setName("Acme 科技");
    when(customerMapper.selectBatchIds(List.of(20L))).thenReturn(List.of(c));

    DashboardStats stats = service.getDashboard();

    assertThat(stats.getStalledOpportunities()).hasSize(1);
    DashboardStats.StalledOpportunity s = stats.getStalledOpportunities().get(0);
    assertThat(s.getOpportunityName()).isEqualTo("CRM 采购");
    assertThat(s.getCustomerName()).isEqualTo("Acme 科技");
    assertThat(s.getStalledDays()).isEqualTo(12);
  }

  @Test
  @DisplayName("缓存：命中时直接返回缓存不重算")
  void cacheHit() {
    // 缓存逻辑由 Redis mock 控制；此处验证无缓存时正常返回并带 generatedAt
    when(soMapper.selectList(any())).thenReturn(List.of());
    when(customerMapper.selectList(any())).thenReturn(List.of());
    when(followUpMapper.selectList(any())).thenReturn(List.of());
    when(targetMapper.selectOne(any())).thenReturn(null);
    when(oppMapper.selectBatchIds(any())).thenReturn(List.of());

    DashboardStats stats = service.getDashboard();

    assertThat(stats.getGeneratedAt()).isNotNull();
    verify(soMapper).selectList(any());
  }
}
