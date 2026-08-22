package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 销售目标（006-sales-dashboard，按月度设置业绩目标）。 */
@Getter
@Setter
@TableName("sales_target")
public class SalesTarget extends BaseEntity {

  /** 目标月份，YYYY-MM。 */
  private String targetMonth;

  /** 目标金额（分）。 */
  private Long targetAmount;

  private Long createdBy;
}
