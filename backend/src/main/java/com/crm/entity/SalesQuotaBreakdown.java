package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** 配额分解关系（078-sales-quota，记录父子配额分解关系）。 */
@Getter
@Setter
@TableName("sales_quota_breakdown")
public class SalesQuotaBreakdown extends BaseEntity {

  /** 上级配额 ID。 */
  private Long parentQuotaId;

  /** 下级配额 ID。 */
  private Long childQuotaId;

  /** 分解金额（万元）。 */
  private BigDecimal amount;

  /** 下级配额详情（非数据库字段，查询时填充）。 */
  @TableField(exist = false)
  private SalesQuota childQuota;
}
