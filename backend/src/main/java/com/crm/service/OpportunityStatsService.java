package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.dto.stats.PipelineStats;
import com.crm.entity.SalesOpportunity;
import com.crm.repository.SalesOpportunityMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/** 商机管道统计（research.md R10，FR-011）。按阶段聚合数量与金额，Redis 缓存 5 分钟。 */
@Service
public class OpportunityStatsService {

  private static final Logger log = LoggerFactory.getLogger(OpportunityStatsService.class);
  private static final String CACHE_KEY = "stats:opportunity-pipeline";

  private final SalesOpportunityMapper salesOpportunityMapper;
  private final RedisTemplate<String, Object> redisTemplate;
  private final OpportunityStageService stageService;

  public OpportunityStatsService(
      SalesOpportunityMapper salesOpportunityMapper,
      RedisTemplate<String, Object> redisTemplate,
      OpportunityStageService stageService) {
    this.salesOpportunityMapper = salesOpportunityMapper;
    this.redisTemplate = redisTemplate;
    this.stageService = stageService;
  }

  @SuppressWarnings("unchecked")
  public PipelineStats getPipeline() {
    Object cached = redisTemplate.opsForValue().get(CACHE_KEY);
    if (cached instanceof PipelineStats stats) {
      return stats;
    }
    PipelineStats stats = compute();
    try {
      redisTemplate.opsForValue().set(CACHE_KEY, stats, Duration.ofMinutes(5));
    } catch (Exception ex) {
      log.warn("Failed to cache pipeline stats: {}", ex.getMessage());
    }
    return stats;
  }

  /** 写操作后调用，使统计缓存失效（SC-006 一致性）。 */
  public void evict() {
    try {
      redisTemplate.delete(CACHE_KEY);
    } catch (Exception ex) {
      log.warn("Failed to evict pipeline stats cache: {}", ex.getMessage());
    }
  }

  private PipelineStats compute() {
    List<SalesOpportunity> all =
        salesOpportunityMapper.selectList(
            new LambdaQueryWrapper<SalesOpportunity>()
                .select(SalesOpportunity::getStage, SalesOpportunity::getAmount));
    Map<String, long[]> acc = new LinkedHashMap<>();
    // 预置全部阶段（含已停用）以保证顺序稳定、且零商机的阶段也出现在漏斗里；
    // 顺序按下单序 sort_order（1.2 起由阶段字典决定，不再是代码里的枚举顺序）。
    for (String stage : stageService.orderedCodes()) {
      acc.put(stage, new long[2]);
    }
    for (SalesOpportunity so : all) {
      long[] cur = acc.computeIfAbsent(so.getStage(), k -> new long[2]);
      cur[0] += 1;
      cur[1] += so.getAmount() == null ? 0L : so.getAmount();
    }
    List<PipelineStats.StageStat> stages = new ArrayList<>();
    long totalCount = 0;
    long totalAmount = 0;
    for (Map.Entry<String, long[]> entry : acc.entrySet()) {
      long count = entry.getValue()[0];
      long amount = entry.getValue()[1];
      totalCount += count;
      totalAmount += amount;
      stages.add(new PipelineStats.StageStat(entry.getKey(), count, amount));
    }
    return new PipelineStats(
        stages, new PipelineStats.GrandTotal(totalCount, totalAmount), LocalDateTime.now());
  }
}
