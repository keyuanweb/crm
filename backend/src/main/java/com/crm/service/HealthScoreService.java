package com.crm.service;

import com.crm.dto.customer.HealthScoreDTO;
import com.crm.dto.customer.HealthScoreDTO.ScoreDeduction;
import com.crm.entity.HealthScoreConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 客户健康度评分服务（018-customer-360，FR-002/003/004）：规则引擎计分。
 *
 * <p>维度与权重可配置（health_score_config 表），默认：跟进活跃度 30 / 回款及时性 25 / 工单服务 20 / 合作深度 15 / 近期互动 10。总分
 * 0-100，映射 RED(&lt;60)/YELLOW(60-79)/GREEN(≥80)。
 */
@Service
public class HealthScoreService {

  private static final Logger log = LoggerFactory.getLogger(HealthScoreService.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** 评分输入：客户业务活动聚合数据（由调用方从 Mapper 查询装配）。 */
  public static class HealthInput {
    private Integer lastFollowUpDays; // 最近跟进距今天数，null=从未跟进
    private int overduePaymentCount; // 逾期回款期次数
    private int openTicketCount; // 未解决工单数（OPEN/IN_PROGRESS）
    private long totalOrderAmount; // 累计订单金额（分）
    private Integer lastActivityDays; // 最近任意业务活动天数，null=无活动

    public static Builder builder() {
      return new Builder();
    }

    public static class Builder {
      private final HealthInput input = new HealthInput();

      public Builder lastFollowUpDays(Integer v) {
        input.lastFollowUpDays = v;
        return this;
      }

      public Builder overduePaymentCount(int v) {
        input.overduePaymentCount = v;
        return this;
      }

      public Builder openTicketCount(int v) {
        input.openTicketCount = v;
        return this;
      }

      public Builder totalOrderAmount(long v) {
        input.totalOrderAmount = v;
        return this;
      }

      public Builder lastActivityDays(Integer v) {
        input.lastActivityDays = v;
        return this;
      }

      public HealthInput build() {
        return input;
      }
    }
  }

  public HealthScoreDTO score(HealthInput input, List<HealthScoreConfig> configs) {
    int totalDeduct = 0;
    List<ScoreDeduction> deductions = new ArrayList<>();
    for (HealthScoreConfig cfg : configs) {
      if (cfg.getEnabled() == null || cfg.getEnabled() != 1) {
        continue;
      }
      int weight = cfg.getWeight() == null ? 0 : cfg.getWeight();
      int deduct = computeDeduction(cfg.getDimensionKey(), cfg, input);
      deduct = Math.max(0, Math.min(weight, deduct));
      if (deduct > 0) {
        totalDeduct += deduct;
        deductions.add(new ScoreDeduction(cfg.getDimensionLabel(), deduct));
      }
    }
    int score = Math.max(0, 100 - totalDeduct);
    String level = score < 60 ? "RED" : score < 80 ? "YELLOW" : "GREEN";
    log.debug("Health score computed: {} ({}), deductions={}", score, level, deductions);
    return new HealthScoreDTO(score, level, deductions);
  }

  private int computeDeduction(String key, HealthScoreConfig cfg, HealthInput input) {
    int weight = cfg.getWeight() == null ? 0 : cfg.getWeight();
    Map<String, Object> params = parseParams(cfg.getParamsJson());
    switch (key) {
      case "FOLLOWUP":
        return followUpDeduct(weight, params, input);
      case "PAYMENT":
        return input.overduePaymentCount > 0 ? weight : 0;
      case "TICKET":
        return Math.min(weight, input.openTicketCount * 10);
      case "DEPTH":
        return depthDeduct(weight, params, input.totalOrderAmount);
      case "ACTIVITY":
        return activityDeduct(weight, params, input.lastActivityDays);
      default:
        return 0;
    }
  }

  /** 跟进活跃度：≤activeDays 不扣；≥zeroDays 扣满；中间线性。 */
  private int followUpDeduct(int weight, Map<String, Object> params, HealthInput input) {
    int activeDays = intParam(params, "activeDays", 30);
    int zeroDays = intParam(params, "zeroDays", 90);
    Integer days = input.lastFollowUpDays;
    if (days == null) {
      return weight;
    }
    if (days <= activeDays) {
      return 0;
    }
    if (days >= zeroDays) {
      return weight;
    }
    return (int) Math.round(weight * (double) (days - activeDays) / (zeroDays - activeDays));
  }

  /** 合作深度：按累计成交金额分档给分（tiers 按 amount 升序），不足最低档扣满。 */
  private int depthDeduct(int weight, Map<String, Object> params, long totalAmount) {
    List<Map<String, Object>> tiers =
        MAPPER.convertValue(params.get("tiers"), new TypeReference<>() {});
    if (tiers == null || tiers.isEmpty()) {
      return 0;
    }
    for (Map<String, Object> tier : tiers) {
      long amount = ((Number) tier.get("amount")).longValue();
      int score = ((Number) tier.get("score")).intValue();
      if (totalAmount >= amount) {
        return Math.max(0, weight - score);
      }
    }
    return weight;
  }

  /** 近期互动：≤activeDays 不扣；null 或超出按比例扣。 */
  private int activityDeduct(int weight, Map<String, Object> params, Integer lastActivityDays) {
    int activeDays = intParam(params, "activeDays", 14);
    Integer days = lastActivityDays;
    if (days == null) {
      return weight;
    }
    if (days <= activeDays) {
      return 0;
    }
    int penaltyDays = Math.max(30, activeDays * 2);
    return days >= penaltyDays
        ? weight
        : (int) Math.round(weight * (double) (days - activeDays) / (penaltyDays - activeDays));
  }

  private Map<String, Object> parseParams(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return MAPPER.readValue(json, new TypeReference<>() {});
    } catch (Exception ex) {
      log.warn("Failed to parse health config params: {}", json, ex);
      return Map.of();
    }
  }

  private int intParam(Map<String, Object> params, String key, int def) {
    Object v = params.get(key);
    return v instanceof Number n ? n.intValue() : def;
  }
}
