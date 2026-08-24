package com.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.crm.entity.FollowUp;
import com.crm.entity.Lead;
import com.crm.entity.LeadScoreConfig;
import com.crm.repository.FollowUpMapper;
import com.crm.repository.LeadScoreConfigMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 线索评分服务（019-lead-scoring，FR-001/002/004）：规则引擎计算 0-100 分并写回 lead.score。 维度：来源渠道 / 信息完整度 / 跟进活跃度 /
 * 互动时效（配置可调）。
 */
@Service
public class LeadScoreService {

  private static final Logger log = LoggerFactory.getLogger(LeadScoreService.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final LeadScoreConfigMapper configMapper;
  private final FollowUpMapper followUpMapper;

  public LeadScoreService(LeadScoreConfigMapper configMapper, FollowUpMapper followUpMapper) {
    this.configMapper = configMapper;
    this.followUpMapper = followUpMapper;
  }

  /** 计算并写回线索评分（创建/更新/跟进后调用）。 */
  public void scoreAndUpdate(Lead lead) {
    int score = computeScore(lead);
    lead.setScore(score);
    log.debug("Lead {} scored: {}", lead.getId(), score);
  }

  /** 计算线索评分（0-100，规则引擎）。 */
  public int computeScore(Lead lead) {
    Map<String, LeadScoreConfig> configs = loadConfigs();
    int total = 0;
    total += sourceScore(lead, configs.get("SOURCE"));
    total += infoScore(lead, configs.get("INFO"));
    total += followUpScore(lead, configs.get("FOLLOWUP"));
    total += freshnessScore(lead, configs.get("FRESHNESS"));
    return Math.max(0, Math.min(100, total));
  }

  /** 计算后直接写回 DB（由调用方在事务内调用）。 */
  public int computeAndPersist(Long leadId, com.crm.repository.LeadMapper leadMapper) {
    Lead lead = leadMapper.selectById(leadId);
    if (lead == null) {
      return 0;
    }
    int score = computeScore(lead);
    lead.setScore(score);
    leadMapper.updateById(lead);
    return score;
  }

  private Map<String, LeadScoreConfig> loadConfigs() {
    List<LeadScoreConfig> list =
        configMapper.selectList(
            new LambdaQueryWrapper<LeadScoreConfig>()
                .eq(LeadScoreConfig::getEnabled, 1)
                .orderByAsc(LeadScoreConfig::getSortOrder));
    return list.stream()
        .collect(java.util.stream.Collectors.toMap(LeadScoreConfig::getRuleKey, c -> c));
  }

  /** 来源渠道分值。 */
  private int sourceScore(Lead lead, LeadScoreConfig cfg) {
    if (cfg == null) {
      return 0;
    }
    Map<String, Object> params = parseParams(cfg.getParamsJson());
    Object v = lead.getSource() == null ? null : params.get(lead.getSource());
    return v instanceof Number n ? n.intValue() : 0;
  }

  /** 信息完整度：每填充一个字段得分。 */
  private int infoScore(Lead lead, LeadScoreConfig cfg) {
    if (cfg == null) {
      return 0;
    }
    Map<String, Object> params = parseParams(cfg.getParamsJson());
    List<String> fields = MAPPER.convertValue(params.get("fields"), new TypeReference<>() {});
    double each = params.get("each") instanceof Number n ? n.doubleValue() : 0;
    if (fields == null) {
      return 0;
    }
    int filled = 0;
    for (String f : fields) {
      String val = fieldValue(lead, f);
      if (val != null && !val.isBlank()) {
        filled++;
      }
    }
    return (int) Math.round(filled * each);
  }

  private String fieldValue(Lead lead, String field) {
    switch (field) {
      case "company":
        return lead.getCompany();
      case "title":
        return lead.getTitle();
      case "phone":
        return lead.getPhone();
      case "email":
        return lead.getEmail();
      default:
        return null;
    }
  }

  /** 跟进活跃度：最近 activeDays 天有跟进满分，越久越低。 */
  private int followUpScore(Lead lead, LeadScoreConfig cfg) {
    int maxScore = cfg == null ? 25 : intParam(parseParams(cfg.getParamsJson()), "maxScore", 25);
    int activeDays = cfg == null ? 7 : intParam(parseParams(cfg.getParamsJson()), "activeDays", 7);
    List<FollowUp> followUps =
        followUpMapper.selectList(
            new LambdaQueryWrapper<FollowUp>()
                .eq(FollowUp::getLeadId, lead.getId())
                .orderByDesc(FollowUp::getCreatedAt));
    if (followUps.isEmpty()) {
      return 0;
    }
    long days = daysBetween(followUps.get(0).getCreatedAt(), LocalDateTime.now());
    if (days <= activeDays) {
      return maxScore;
    }
    int zeroDays = Math.max(30, activeDays * 3);
    return days >= zeroDays
        ? 0
        : (int) Math.round(maxScore * (1 - (double) (days - activeDays) / (zeroDays - activeDays)));
  }

  /** 互动时效：线索创建时间越近越高。 */
  private int freshnessScore(Lead lead, LeadScoreConfig cfg) {
    int maxScore = cfg == null ? 15 : intParam(parseParams(cfg.getParamsJson()), "maxScore", 15);
    int activeDays = cfg == null ? 7 : intParam(parseParams(cfg.getParamsJson()), "activeDays", 7);
    int zeroDays = cfg == null ? 30 : intParam(parseParams(cfg.getParamsJson()), "zeroDays", 30);
    if (lead.getCreatedAt() == null) {
      return maxScore;
    }
    long days = daysBetween(lead.getCreatedAt(), LocalDateTime.now());
    if (days <= activeDays) {
      return maxScore;
    }
    return days >= zeroDays
        ? 0
        : (int) Math.round(maxScore * (1 - (double) (days - activeDays) / (zeroDays - activeDays)));
  }

  private long daysBetween(LocalDateTime from, LocalDateTime to) {
    return from == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(from, to));
  }

  private Map<String, Object> parseParams(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return MAPPER.readValue(json, new TypeReference<>() {});
    } catch (Exception ex) {
      log.warn("Failed to parse lead score config: {}", json, ex);
      return Map.of();
    }
  }

  private int intParam(Map<String, Object> params, String key, int def) {
    Object v = params.get(key);
    return v instanceof Number n ? n.intValue() : def;
  }
}
