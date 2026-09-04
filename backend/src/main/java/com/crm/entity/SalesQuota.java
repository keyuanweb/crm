package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** 销售配额（078-sales-quota，支持年度/季度/团队/个人维度）。 */
@Getter
@Setter
@TableName("sales_quota")
public class SalesQuota extends BaseEntity {

  /** 上级配额 ID（年度配额为 NULL）。 */
  private Long parentId;

  /** 季度（1-4，年度配额为 NULL）。 */
  private Integer quarter;

  /** 年份。 */
  private Integer year;

  /** 团队 ID（个人配额为 NULL）。 */
  private Long teamId;

  /** 用户 ID（团队配额为 NULL）。 */
  private Long userId;

  /** 配额金额（万元，保留 2 位小数）。 */
  private BigDecimal amount;

  /** 状态：DRAFT/ACTIVE/CLOSED。 */
  private String status;

  /** 期间开始日期。 */
  private LocalDate periodStart;

  /** 期间结束日期。 */
  private LocalDate periodEnd;

  /** 实际销售额（万元，实时聚合计算）。 */
  @TableField(exist = false)
  private BigDecimal actualAmount;

  /** 达成率（%）。 */
  @TableField(exist = false)
  private BigDecimal achievementRate;
}
