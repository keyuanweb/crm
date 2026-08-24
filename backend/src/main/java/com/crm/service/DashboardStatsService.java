package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/** 销售仪表盘聚合（006-sales-dashboard，FR-D01~D10）。Redis 缓存 5 分钟，写操作后失效。 */
@Service
public class DashboardStatsService {

  private static final Logger log = LoggerFactory.getLogger(DashboardStatsService.class);
  private static final String CACHE_KEY = "stats:dashboard";
  private static final List<String> STAGE_ORDER =
      List.of("INITIAL_CONTACT", "NEGOTIATING", "CLOSED_WON", "CLOSED_LOST");
  private static final Map<String, Double> STAGE_PROBABILITY =
      Map.of(
          "INITIAL_CONTACT", 0.2,
          "NEGOTIATING", 0.5,
          "CLOSED_WON", 1.0,
          "CLOSED_LOST", 0.0);

  private final SalesOpportunityMapper salesOpportunityMapper;
  private final OpportunityMapper opportunityMapper;
  private final CustomerMapper customerMapper;
  private final FollowUpMapper followUpMapper;
  private final SalesTargetMapper salesTargetMapper;
  private final UserMapper userMapper;
  private final RedisTemplate<String, Object> redisTemplate;
  private final StageConversionService stageConversionService;

  /** 停滞预警阈值（天），默认 7，可配置 crm.stats.stalled-days。 */
  @Value("${crm.stats.stalled-days:7}")
  private int stalledDays;

  public DashboardStatsService(
      SalesOpportunityMapper salesOpportunityMapper,
      OpportunityMapper opportunityMapper,
      CustomerMapper customerMapper,
      FollowUpMapper followUpMapper,
      SalesTargetMapper salesTargetMapper,
      UserMapper userMapper,
      RedisTemplate<String, Object> redisTemplate,
      StageConversionService stageConversionService) {
    this.salesOpportunityMapper = salesOpportunityMapper;
    this.opportunityMapper = opportunityMapper;
    this.customerMapper = customerMapper;
    this.followUpMapper = followUpMapper;
    this.salesTargetMapper = salesTargetMapper;
    this.userMapper = userMapper;
    this.redisTemplate = redisTemplate;
    this.stageConversionService = stageConversionService;
  }

  @SuppressWarnings("unchecked")
  public DashboardStats getDashboard() {
    Object cached = redisTemplate.opsForValue().get(CACHE_KEY);
    if (cached instanceof DashboardStats stats) {
      return stats;
    }
    DashboardStats stats = compute();
    try {
      redisTemplate.opsForValue().set(CACHE_KEY, stats, Duration.ofMinutes(5));
    } catch (Exception ex) {
      log.warn("Failed to cache dashboard stats: {}", ex.getMessage());
    }
    return stats;
  }

  /** 业务写操作后调用，使仪表盘缓存失效（FR-D10）。 */
  public void evict() {
    try {
      redisTemplate.delete(CACHE_KEY);
    } catch (Exception ex) {
      log.warn("Failed to evict dashboard stats cache: {}", ex.getMessage());
    }
  }

  private DashboardStats compute() {
    List<SalesOpportunity> allSo =
        salesOpportunityMapper.selectList(
            new LambdaQueryWrapper<SalesOpportunity>()
                .select(
                    SalesOpportunity::getId,
                    SalesOpportunity::getOpportunityId,
                    SalesOpportunity::getStage,
                    SalesOpportunity::getAmount,
                    SalesOpportunity::getClosedAt,
                    SalesOpportunity::getUpdatedAt));
    YearMonth month = YearMonth.now();
    DashboardStats.Summary summary = computeSummary(allSo, month);
    DashboardStats.Funnel funnel = computeFunnel(allSo);
    DashboardStats.Forecast forecast = computeForecast(allSo);
    DashboardStats.Performance performance = computePerformance(month);
    DashboardStats.FollowUps followUps = computeFollowUps();
    List<DashboardStats.StalledOpportunity> stalled = computeStalled(allSo);
    return new DashboardStats(
        summary, funnel, forecast, performance, followUps, stalled, LocalDateTime.now());
  }

  private DashboardStats.Summary computeSummary(List<SalesOpportunity> allSo, YearMonth month) {
    long count = allSo.size();
    long amountTotal = 0;
    long won = 0;
    long closed = 0;
    for (SalesOpportunity so : allSo) {
      amountTotal += so.getAmount() == null ? 0L : so.getAmount();
      if ("CLOSED_WON".equals(so.getStage())) {
        won++;
        closed++;
      } else if ("CLOSED_LOST".equals(so.getStage())) {
        closed++;
      }
    }
    double winRate = closed == 0 ? 0d : (double) won / closed;

    List<Customer> customers =
        customerMapper.selectList(
            new LambdaQueryWrapper<Customer>()
                .select(Customer::getId, Customer::getStatus, Customer::getCreatedAt));
    long customerCount = customers.size();
    long activeCount = customers.stream().filter(c -> "ACTIVE".equals(c.getStatus())).count();
    LocalDateTime monthStart = month.atDay(1).atStartOfDay();
    LocalDateTime monthEnd = month.atEndOfMonth().plusDays(1).atStartOfDay();
    long newThisMonth =
        customers.stream()
            .filter(
                c ->
                    c.getCreatedAt() != null
                        && !c.getCreatedAt().isBefore(monthStart)
                        && c.getCreatedAt().isBefore(monthEnd))
            .count();
    return new DashboardStats.Summary(
        count, amountTotal, winRate, customerCount, activeCount, newThisMonth);
  }

  private DashboardStats.Funnel computeFunnel(List<SalesOpportunity> allSo) {
    Map<String, long[]> acc = new LinkedHashMap<>();
    for (String stage : STAGE_ORDER) {
      acc.put(stage, new long[2]);
    }
    for (SalesOpportunity so : allSo) {
      long[] cur = acc.computeIfAbsent(so.getStage(), k -> new long[2]);
      cur[0] += 1;
      cur[1] += so.getAmount() == null ? 0L : so.getAmount();
    }
    List<DashboardStats.StageStat> stages = new ArrayList<>();
    long totalCount = 0;
    long totalAmount = 0;
    DashboardStats.StageStat prev = null;
    for (Map.Entry<String, long[]> entry : acc.entrySet()) {
      long count = entry.getValue()[0];
      long amount = entry.getValue()[1];
      totalCount += count;
      totalAmount += amount;
      Double rate = (prev != null && prev.getCount() > 0) ? (double) count / prev.getCount() : null;
      DashboardStats.StageStat stat =
          new DashboardStats.StageStat(entry.getKey(), count, amount, rate);
      stages.add(stat);
      prev = stat;
    }
    DashboardStats.StageStat grandTotal =
        new DashboardStats.StageStat("TOTAL", totalCount, totalAmount, null);
    return new DashboardStats.Funnel(stages, grandTotal);
  }

  private DashboardStats.Forecast computeForecast(List<SalesOpportunity> allSo) {
    List<DashboardStats.ForecastItem> breakdown = new ArrayList<>();
    long weightedTotal = 0;
    for (SalesOpportunity so : allSo) {
      // 019：用历史校准概率（样本不足回退默认），替代硬编码 STAGE_PROBABILITY
      double probability = stageConversionService.probabilityFor(so.getStage());
      String source = probabilitySourceOf(so.getStage());
      long amount = so.getAmount() == null ? 0L : so.getAmount();
      long weighted = Math.round(amount * probability);
      weightedTotal += weighted;
      breakdown.add(
          new DashboardStats.ForecastItem(so.getStage(), amount, probability, weighted, source));
    }
    return new DashboardStats.Forecast(weightedTotal, breakdown);
  }

  /** 概率来源标注：CLOSED 终态 FIXED，其余由 StageConversionService 判定（HISTORICAL/DEFAULT）。 */
  private String probabilitySourceOf(String stage) {
    if ("CLOSED_WON".equals(stage) || "CLOSED_LOST".equals(stage)) {
      return "FIXED";
    }
    return stageConversionService.isHistorical(stage) ? "HISTORICAL" : "DEFAULT";
  }

  private DashboardStats.Performance computePerformance(YearMonth month) {
    // 020：优先当前用户个人目标，未设置回退全局目标（user_id IS NULL）
    Long currentUserId = null;
    try {
      currentUserId = com.crm.security.SecurityUtil.currentUserId();
    } catch (Exception ex) {
      log.debug("No authenticated user for personal target lookup: {}", ex.getMessage());
    }
    SalesTarget target = null;
    boolean personal = false;
    if (currentUserId != null) {
      target =
          salesTargetMapper.selectOne(
              new LambdaQueryWrapper<SalesTarget>()
                  .eq(SalesTarget::getTargetMonth, month.toString())
                  .eq(SalesTarget::getUserId, currentUserId));
      if (target != null) {
        personal = true;
      }
    }
    if (target == null) {
      target =
          salesTargetMapper.selectOne(
              new LambdaQueryWrapper<SalesTarget>()
                  .eq(SalesTarget::getTargetMonth, month.toString())
                  .isNull(SalesTarget::getUserId));
    }
    Long targetAmount = target == null ? null : target.getTargetAmount();
    if (targetAmount == null) {
      return new DashboardStats.Performance(month.toString(), null, null, null, false, personal);
    }
    LocalDateTime monthStart = month.atDay(1).atStartOfDay();
    LocalDateTime monthEnd = month.atEndOfMonth().plusDays(1).atStartOfDay();
    List<SalesOpportunity> wonSo =
        salesOpportunityMapper.selectList(
            new LambdaQueryWrapper<SalesOpportunity>()
                .eq(SalesOpportunity::getStage, "CLOSED_WON")
                .ge(SalesOpportunity::getClosedAt, monthStart)
                .lt(SalesOpportunity::getClosedAt, monthEnd));
    long wonAmount =
        wonSo.stream().mapToLong(so -> so.getAmount() == null ? 0L : so.getAmount()).sum();
    double rate = targetAmount == 0 ? 0d : (double) wonAmount / targetAmount;
    return new DashboardStats.Performance(
        month.toString(), targetAmount, wonAmount, rate, true, personal);
  }

  private DashboardStats.FollowUps computeFollowUps() {
    List<FollowUp> all =
        followUpMapper.selectList(
            new LambdaQueryWrapper<FollowUp>()
                .select(
                    FollowUp::getId,
                    FollowUp::getCustomerId,
                    FollowUp::getMethod,
                    FollowUp::getContent,
                    FollowUp::getFollowUpBy,
                    FollowUp::getCreatedAt)
                .orderByDesc(FollowUp::getCreatedAt));
    long total = all.size();
    Map<String, Long> byMethod =
        all.stream()
            .collect(
                Collectors.groupingBy(
                    FollowUp::getMethod, LinkedHashMap::new, Collectors.counting()));
    List<DashboardStats.MethodStat> methodStats =
        byMethod.entrySet().stream()
            .map(e -> new DashboardStats.MethodStat(e.getKey(), e.getValue()))
            .toList();

    // 最近 10 条：批量装配客户名与跟进人，避免 N+1（章程原则五）
    List<FollowUp> recentList = all.stream().limit(10).toList();
    Map<Long, String> customerNames =
        customerNamesById(recentList.stream().map(FollowUp::getCustomerId).toList());
    Map<Long, String> userNames =
        userNamesById(recentList.stream().map(FollowUp::getFollowUpBy).toList());
    List<DashboardStats.RecentFollowUp> recent =
        recentList.stream()
            .map(
                f ->
                    new DashboardStats.RecentFollowUp(
                        f.getId(),
                        f.getMethod(),
                        f.getContent(),
                        customerNames.get(f.getCustomerId()),
                        userNames.get(f.getFollowUpBy()),
                        f.getCreatedAt()))
            .toList();
    return new DashboardStats.FollowUps(total, methodStats, recent);
  }

  private List<DashboardStats.StalledOpportunity> computeStalled(List<SalesOpportunity> allSo) {
    LocalDateTime cutoff = LocalDateTime.now().minusDays(stalledDays);
    List<SalesOpportunity> stalledSo =
        allSo.stream()
            .filter(
                so ->
                    ("INITIAL_CONTACT".equals(so.getStage()) || "NEGOTIATING".equals(so.getStage()))
                        && (so.getUpdatedAt() == null || so.getUpdatedAt().isBefore(cutoff)))
            .sorted(
                Comparator.comparing(
                    SalesOpportunity::getUpdatedAt,
                    Comparator.nullsFirst(Comparator.naturalOrder())))
            .toList();
    if (stalledSo.isEmpty()) {
      return List.of();
    }
    List<Opportunity> opps =
        opportunityMapper.selectBatchIds(
            stalledSo.stream()
                .map(SalesOpportunity::getOpportunityId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
    Map<Long, Opportunity> oppById =
        opps.stream().collect(Collectors.toMap(Opportunity::getId, o -> o, (a, b) -> a));
    Map<Long, String> customerNames =
        customerNamesById(
            opps.stream().map(Opportunity::getCustomerId).filter(Objects::nonNull).toList());
    return stalledSo.stream()
        .map(
            so -> {
              Opportunity opp = oppById.get(so.getOpportunityId());
              return new DashboardStats.StalledOpportunity(
                  so.getId(),
                  opp == null ? null : opp.getName(),
                  opp == null ? null : customerNames.get(opp.getCustomerId()),
                  so.getAmount(),
                  so.getStage(),
                  Duration.between(
                          so.getUpdatedAt() == null ? LocalDateTime.now() : so.getUpdatedAt(),
                          LocalDateTime.now())
                      .toDays(),
                  so.getUpdatedAt());
            })
        .toList();
  }

  private Map<Long, String> customerNamesById(List<Long> ids) {
    List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
    Map<Long, String> names = new HashMap<>();
    if (distinct.isEmpty()) {
      return names;
    }
    for (Customer c : customerMapper.selectBatchIds(distinct)) {
      if (c != null && c.getId() != null && c.getName() != null) {
        names.put(c.getId(), c.getName());
      }
    }
    return names;
  }

  private Map<Long, String> userNamesById(List<Long> ids) {
    List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
    Map<Long, String> names = new HashMap<>();
    if (distinct.isEmpty()) {
      return names;
    }
    for (User u : userMapper.selectBatchIds(distinct)) {
      if (u != null && u.getId() != null) {
        names.put(u.getId(), u.getDisplayName() != null ? u.getDisplayName() : u.getUsername());
      }
    }
    return names;
  }
}
