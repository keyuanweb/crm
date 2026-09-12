package com.crm.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** SLA 策略（015-customer-service，按优先级配置时限）。 */
@Getter
@Setter
@TableName("sla_policy")
public class SlaPolicy extends BaseEntity {

  /** LOW / MEDIUM / HIGH / URGENT（唯一）。 */
  private String priority;

  /** 响应时限（小时，null=不约束）。 */
  private Integer respondHours;

  /** 解决时限（小时，null=不约束）。 */
  private Integer resolveHours;

  private Integer enabled;

  @TableField(fill = FieldFill.INSERT)
  private Long createdBy;
}
