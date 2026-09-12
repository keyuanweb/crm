package com.crm.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 销售目标（006-sales-dashboard / 020-sales-targets，按月度设置业绩目标，支持个人维度）。 */
@Getter
@Setter
@TableName("sales_target")
public class SalesTarget extends BaseEntity {

  /** 目标月份，YYYY-MM。 */
  private String targetMonth;

  /** 目标金额（分）。 */
  private Long targetAmount;

  /** 归属用户（NULL=全局目标；非 NULL=个人目标，020）。 */
  private Long userId;

  @TableField(fill = FieldFill.INSERT)
  private Long createdBy;
}
