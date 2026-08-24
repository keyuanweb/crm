package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.PageResult;
import com.crm.dto.customer.CustomerResponse;
import com.crm.dto.stats.DashboardStats;
import com.crm.dto.stats.HealthDistribution;
import com.crm.dto.stats.KpiBoardResponse;
import com.crm.dto.stats.LeaderboardItem;
import com.crm.dto.stats.TrendPoint;
import com.crm.entity.SalesOpportunity;
import com.crm.repository.SalesOpportunityMapper;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/** KPI 大屏聚合服务（023-kpi-dashboard，FR-001/008）：组合 dashboard/排行/建议/健康度分布/趋势， Redis 缓存 5 分钟。 */
@Service
public class KpiBoardService {

  private static final Logger log = LoggerFactory.getLogger(KpiBoardService.class);
  private static final String CACHE_KEY = "stats:kpi-board";
  private static final Duration CACHE_TTL = Duration.ofMinutes(5);
  private static final int HEALTH_SAMPLE_CAP = 200;
  private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private final DashboardStatsService dashboardStatsService;
  private final TeamLeaderboardService leaderboardService;
  private final SuggestionService suggestionService;
  private final CustomerService customerService;
  private final Customer360Service customer360Service;
  private final SalesOpportunityMapper soMapper;
  private final RedisTemplate<String, Object> redisTemplate;

  public KpiBoardService(
      DashboardStatsService dashboardStatsService,
      TeamLeaderboardService leaderboardService,
      SuggestionService suggestionService,
      CustomerService customerService,
      Customer360Service customer360Service,
      SalesOpportunityMapper soMapper,
      RedisTemplate<String, Object> redisTemplate) {
    this.dashboardStatsService = dashboardStatsService;
    this.leaderboardService = leaderboardService;
    this.suggestionService = suggestionService;
    this.customerService = customerService;
    this.customer360Service = customer360Service;
    this.soMapper = soMapper;
    this.redisTemplate = redisTemplate;
  }

  @SuppressWarnings("unchecked")
  public KpiBoardResponse getKpiBoard() {
    Object cached = redisTemplate.opsForValue().get(CACHE_KEY);
    if (cached instanceof KpiBoardResponse resp) {
      return resp;
    }
    KpiBoardResponse resp = compute();
    try {
      redisTemplate.opsForValue().set(CACHE_KEY, resp, CACHE_TTL);
    } catch (Exception ex) {
      log.warn("Failed to cache kpi board: {}", ex.getMessage());
    }
    return resp;
  }

  private KpiBoardResponse compute() {
    KpiBoardResponse resp = new KpiBoardResponse();

    DashboardStats stats = dashboardStatsService.getDashboard();
    resp.setKpi(stats.getSummary());
    resp.setFunnel(stats.getFunnel());

    String month = java.time.YearMonth.now().toString();
    List<LeaderboardItem> board = leaderboardService.leaderboard(month, "rate");
    resp.setLeaderboard(board.size() > 10 ? board.subList(0, 10) : board);

    resp.setSuggestions(suggestionService.summary());
    resp.setHealthDistribution(computeHealthDistribution());
    resp.setTrend(computeTrend());
    return resp;
  }

  /** 健康度分布：抽查最多 200 个可见客户，批量装配健康度等级计数（避免逐客户 N+1）。 */
  private HealthDistribution computeHealthDistribution() {
    HealthDistribution hd = new HealthDistribution();
    try {
      PageResult<CustomerResponse> page =
          customerService.page(null, null, null, 1, HEALTH_SAMPLE_CAP);
      List<Long> ids = page.getItems().stream().map(CustomerResponse::getId).toList();
      Map<Long, String> levels = customer360Service.healthLevelsBatch(ids);
      for (String level : levels.values()) {
        switch (level) {
          case "RED" -> hd.setRed(hd.getRed() + 1);
          case "YELLOW" -> hd.setYellow(hd.getYellow() + 1);
          default -> hd.setGreen(hd.getGreen() + 1);
        }
      }
    } catch (Exception ex) {
      log.warn("Failed to compute health distribution: {}", ex.getMessage());
    }
    return hd;
  }

  /** 近 30 天商机趋势（按日数量/金额）。 */
  private List<TrendPoint> computeTrend() {
    LocalDateTime start = LocalDate.now().minusDays(29).atStartOfDay();
    List<SalesOpportunity> list =
        soMapper.selectList(
            new LambdaQueryWrapper<SalesOpportunity>()
                .ge(SalesOpportunity::getCreatedAt, start)
                .select(SalesOpportunity::getCreatedAt, SalesOpportunity::getAmount));
    Map<String, long[]> acc = new LinkedHashMap<>();
    for (SalesOpportunity s : list) {
      if (s.getCreatedAt() == null) {
        continue;
      }
      String key = s.getCreatedAt().format(DAY_FMT);
      long[] cur = acc.computeIfAbsent(key, k -> new long[2]);
      cur[0] += 1;
      cur[1] += s.getAmount() == null ? 0L : s.getAmount();
    }
    List<TrendPoint> trend = new ArrayList<>();
    for (Map.Entry<String, long[]> e : acc.entrySet()) {
      TrendPoint p = new TrendPoint();
      p.setDate(e.getKey());
      p.setCount(e.getValue()[0]);
      p.setAmount(e.getValue()[1]);
      trend.add(p);
    }
    trend.sort(Comparator.comparing(TrendPoint::getDate));
    return trend;
  }
}
