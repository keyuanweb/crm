package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 配额达成统计（078-sales-quota，记录配额达成情况）。 */
@Getter
@Setter
@TableName("sales_quota_achievement")
public class SalesQuotaAchievement extends BaseEntity {

  /** 配额 ID。 */
  private Long quotaId;

  /** 实际销售额（万元）。 */
  private BigDecimal actualAmount;

  /** 达成率（%）。 */
  private BigDecimal achievementRate;

  /** 计算时间。 */
  private LocalDateTime calculatedAt;

  /** 配额年份（冗余字段，便于查询）。 */
  private Integer quotaYear;

  /** 配额季度（冗余字段，便于查询）。 */
  private Integer quotaQuarter;

  /** 配额团队 ID（冗余字段，便于查询）。 */
  private Long quotaTeamId;

  /** 配额用户 ID（冗余字段，便于查询）。 */
  private Long quotaUserId;
}
