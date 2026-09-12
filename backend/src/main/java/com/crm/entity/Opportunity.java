package com.crm.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 商机（父实体，data-model.md §3）。 */
@Getter
@Setter
@TableName("opportunity")
public class Opportunity extends BaseEntity {

  private Long customerId;
  private String name;
  private Long expectedAmountMin;
  private Long expectedAmountMax;

  /** 商机金额（用于合规导出汇总）。 */
  private Long amount;

  private String remark;

  /** ACTIVE / ARCHIVED。 */
  private String status;

  @TableField(fill = FieldFill.INSERT)
  private Long createdBy;
}
