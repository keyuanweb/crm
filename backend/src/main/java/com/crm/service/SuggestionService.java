package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.common.PageResult;
import com.crm.dto.customer.CustomerHealthBrief;
import com.crm.dto.suggestion.SmartSuggestion;
import com.crm.dto.suggestion.SuggestionSummary;
import com.crm.entity.FollowUp;
import com.crm.entity.Lead;
import com.crm.entity.SalesOpportunity;
import com.crm.repository.FollowUpMapper;
import com.crm.repository.LeadMapper;
import com.crm.repository.SalesOpportunityMapper;
import com.crm.security.SecurityUtil;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * AI 智能建议服务（022-ai-assistant，FR-001~007）：规则引擎聚合四类待办（客户流失/商机停滞/待跟进/高分线索）， 按优先级排序、去重、忽略过滤、上限截断。忽略记录存
 * Redis。
 */
@Service
public class SuggestionService {

  private static final Logger log = LoggerFactory.getLogger(SuggestionService.class);
  private static final String IGNORE_PREFIX = "ai:ignore:";
  private static final Duration IGNORE_TTL = Duration.ofDays(90);
  private static final int AT_RISK_DAYS = 45;
  private static final int STALLED_DAYS = 7;
  private static final int FOLLOWUP_MIN_DAYS = 15;
  private static final int FOLLOWUP_MAX_DAYS = 45;
  private static final int HIGH_SCORE_THRESHOLD = 70;

  private final CustomerService customerService;
  private final FollowUpMapper followUpMapper;
  private final SalesOpportunityMapper soMapper;
  private final LeadMapper leadMapper;
  private final RedisTemplate<String, Object> redisTemplate;

  public SuggestionService(
      CustomerService customerService,
      FollowUpMapper followUpMapper,
      SalesOpportunityMapper soMapper,
      LeadMapper leadMapper,
      RedisTemplate<String, Object> redisTemplate) {
    this.customerService = customerService;
    this.followUpMapper = followUpMapper;
    this.soMapper = soMapper;
    this.leadMapper = leadMapper;
    this.redisTemplate = redisTemplate;
  }

  /** 生成建议列表（按优先级排序，上限 limit）。 */
  public List<SmartSuggestion> suggestions(int limit) {
    Set<String> ignored = ignoredKeys();
    List<SmartSuggestion> items = new ArrayList<>();

    // 1. 客户流失预警（018）
    PageResult<CustomerHealthBrief> atRisk = customerService.atRiskCustomers(AT_RISK_DAYS, 1, 200);
    for (CustomerHealthBrief b : atRisk.getItems()) {
      if (isIgnored(ignored, "CUSTOMER_AT_RISK", "CUSTOMER", b.getId())) {
        continue;
      }
      items.add(
          suggestion(
              SmartSuggestion.TYPE_AT_RISK,
              "跟进客户：" + b.getName(),
              "已 " + b.getDaysInactive() + " 天无跟进且无新订单，健康度 " + b.getHealthScore(),
              "URGENT",
              "CUSTOMER",
              b.getId(),
              "follow_up"));
    }

    // 2. 商机停滞预警（>7 天未更新活跃机会）
    LocalDateTime stalledCutoff = LocalDateTime.now().minusDays(STALLED_DAYS);
    List<SalesOpportunity> stalled =
        soMapper.selectList(
            new LambdaQueryWrapper<SalesOpportunity>()
                .in(SalesOpportunity::getStage, List.of("INITIAL_CONTACT", "NEGOTIATING"))
                .isNotNull(SalesOpportunity::getUpdatedAt)
                .lt(SalesOpportunity::getUpdatedAt, stalledCutoff));
    for (SalesOpportunity s : stalled) {
      if (isIgnored(ignored, SmartSuggestion.TYPE_STALLED, "OPPORTUNITY", s.getOpportunityId())) {
        continue;
      }
      long days = daysBetween(s.getUpdatedAt(), LocalDateTime.now());
      items.add(
          suggestion(
              SmartSuggestion.TYPE_STALLED,
              "推进商机 #" + s.getOpportunityId(),
              "已停滞 " + days + " 天未更新（阶段：" + s.getStage() + "）",
              "URGENT",
              "OPPORTUNITY",
              s.getOpportunityId(),
              "push"));
    }

    // 3. 待跟进客户（最近跟进 15-45 天）
    List<FollowUp> recent =
        followUpMapper.selectList(
            new LambdaQueryWrapper<FollowUp>()
                .isNotNull(FollowUp::getCustomerId)
                .select(FollowUp::getCustomerId, FollowUp::getCreatedAt)
                .orderByDesc(FollowUp::getCreatedAt));
    // 取每位客户最近一次跟进
    java.util.Map<Long, LocalDateTime> lastFollowUp =
        recent.stream()
            .collect(
                Collectors.toMap(
                    FollowUp::getCustomerId,
                    FollowUp::getCreatedAt,
                    (a, b) -> a.isAfter(b) ? a : b));
    Set<Long> dedup = new LinkedHashSet<>();
    for (java.util.Map.Entry<Long, LocalDateTime> e : lastFollowUp.entrySet()) {
      long days = daysBetween(e.getValue(), LocalDateTime.now());
      if (days < FOLLOWUP_MIN_DAYS || days > FOLLOWUP_MAX_DAYS) {
        continue;
      }
      // 客户同时命中流失（在 atRisk 中）→ 跳过（去重，保留流失）
      boolean atRiskAlready =
          atRisk.getItems().stream().anyMatch(b -> b.getId().equals(e.getKey()));
      if (atRiskAlready
          || isIgnored(ignored, SmartSuggestion.TYPE_FOLLOWUP, "CUSTOMER", e.getKey())) {
        continue;
      }
      dedup.add(e.getKey());
      items.add(
          suggestion(
              SmartSuggestion.TYPE_FOLLOWUP,
              "联系客户 #" + e.getKey(),
              "已 " + days + " 天未跟进，建议联系",
              "IMPORTANT",
              "CUSTOMER",
              e.getKey(),
              "follow_up"));
    }

    // 4. 高分线索待处理（评分 ≥70 且未转化）
    List<Lead> highScoreLeads =
        leadMapper.selectList(
            new LambdaQueryWrapper<Lead>()
                .ge(Lead::getScore, HIGH_SCORE_THRESHOLD)
                .isNull(Lead::getConvertedCustomerId));
    for (Lead l : highScoreLeads) {
      if (isIgnored(ignored, SmartSuggestion.TYPE_HIGH_SCORE_LEAD, "LEAD", l.getId())) {
        continue;
      }
      items.add(
          suggestion(
              SmartSuggestion.TYPE_HIGH_SCORE_LEAD,
              "处理线索：" + l.getName(),
              "评分 " + l.getScore() + "，建议尽快跟进或转化",
              "NORMAL",
              "LEAD",
              l.getId(),
              "process"));
    }

    // 排序：URGENT > IMPORTANT > NORMAL，同优先级按实体 id
    items.sort(
        Comparator.comparingInt(SuggestionService::priorityRank)
            .thenComparing(SmartSuggestion::getEntityId));
    log.debug("Generated {} suggestions", items.size());
    return items.size() > limit ? new ArrayList<>(items.subList(0, limit)) : items;
  }

  /** 建议摘要（FR-008）。 */
  public SuggestionSummary summary() {
    List<SmartSuggestion> all = suggestions(1000);
    SuggestionSummary s = new SuggestionSummary();
    for (SmartSuggestion it : all) {
      switch (it.getType()) {
        case SmartSuggestion.TYPE_AT_RISK -> s.setAtRiskCustomers(s.getAtRiskCustomers() + 1);
        case SmartSuggestion.TYPE_STALLED ->
            s.setStalledOpportunities(s.getStalledOpportunities() + 1);
        case SmartSuggestion.TYPE_FOLLOWUP -> s.setFollowUpCustomers(s.getFollowUpCustomers() + 1);
        case SmartSuggestion.TYPE_HIGH_SCORE_LEAD -> s.setHighScoreLeads(s.getHighScoreLeads() + 1);
        default -> {}
      }
    }
    return s;
  }

  /** 标记忽略（TTL 90 天）。 */
  public void ignore(String type, String entityType, Long entityId) {
    Long userId = SecurityUtil.currentUserId();
    try {
      String key = IGNORE_PREFIX + userId + ":";
      redisTemplate.opsForSet().add(key, entityType + ":" + entityId);
      redisTemplate.expire(key, IGNORE_TTL);
    } catch (Exception ex) {
      log.warn("Failed to record suggestion ignore: {}", ex.getMessage());
    }
  }

  private Set<String> ignoredKeys() {
    Long userId = SecurityUtil.currentUserId();
    try {
      Set<Object> members = redisTemplate.opsForSet().members(IGNORE_PREFIX + userId + ":");
      return members == null
          ? Set.of()
          : members.stream().map(Object::toString).collect(Collectors.toSet());
    } catch (Exception ex) {
      log.warn("Failed to read suggestion ignores: {}", ex.getMessage());
      return Set.of();
    }
  }

  private boolean isIgnored(Set<String> ignored, String type, String entityType, Long entityId) {
    return ignored.contains(entityType + ":" + entityId);
  }

  private SmartSuggestion suggestion(
      String type,
      String title,
      String reason,
      String priority,
      String entityType,
      Long entityId,
      String action) {
    SmartSuggestion s = new SmartSuggestion();
    s.setType(type);
    s.setTitle(title);
    s.setReason(reason);
    s.setPriority(priority);
    s.setEntityType(entityType);
    s.setEntityId(entityId);
    s.setAction(action);
    return s;
  }

  private static int priorityRank(SmartSuggestion s) {
    return switch (s.getPriority()) {
      case "URGENT" -> 0;
      case "IMPORTANT" -> 1;
      default -> 2;
    };
  }

  private long daysBetween(LocalDateTime from, LocalDateTime to) {
    return from == null ? 0 : Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(from, to));
  }
}
