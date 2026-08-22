package com.crm.dto.stats;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 销售仪表盘聚合响应（contracts/stats.md，006 模块）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStats {

  private Summary summary;
  private Funnel funnel;
  private Forecast forecast;
  private Performance performance;
  private FollowUps followUps;
  private List<StalledOpportunity> stalledOpportunities;
  private LocalDateTime generatedAt;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Summary {
    private long opportunityCount;
    private long amountTotal;

    /** 赢单率：CLOSED_WON / (CLOSED_WON + CLOSED_LOST)，无已关闭机会时为 0。 */
    private double winRate;

    private long customerCount;
    private long activeCustomerCount;
    private long newCustomersThisMonth;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Funnel {
    private List<StageStat> stages;
    private StageStat grandTotal;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class StageStat {
    private String stage;
    private long count;
    private long amountTotal;

    /** 后一阶段数量 / 前一阶段数量，首阶段与终态后为 null。 */
    private Double conversionRate;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Forecast {
    private long weightedAmount;
    private List<ForecastItem> breakdown;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ForecastItem {
    private String stage;
    private long amount;
    private double probability;
    private long weighted;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Performance {
    private String month;
    private Long targetAmount;
    private Long wonAmount;
    private Double achievementRate;
    private boolean configured;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class FollowUps {
    private long total;
    private List<MethodStat> byMethod;
    private List<RecentFollowUp> recent;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class MethodStat {
    private String method;
    private long count;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class RecentFollowUp {
    private Long id;
    private String method;
    private String content;
    private String customerName;
    private String followUpBy;
    private LocalDateTime createdAt;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class StalledOpportunity {
    private Long id;
    private String opportunityName;
    private String customerName;
    private Long amount;
    private String stage;
    private long stalledDays;
    private LocalDateTime lastUpdatedAt;
  }
}
