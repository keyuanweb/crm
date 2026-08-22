package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 工作流规则（013-workflow-automation）。 */
@Getter
@Setter
@TableName("workflow_rule")
public class WorkflowRule extends BaseEntity {

  private String name;

  /** LEAD_CREATED / OPPORTUNITY_STAGE_CHANGED / FOLLOW_UP_CREATED / PAYMENT_RECORDED。 */
  private String eventType;

  /** 条件 JSON（field/value）。 */
  private String conditionJson;

  /** CREATE_TASK / ASSIGN / NOTIFY。 */
  private String actionType;

  /** 动作 JSON。 */
  private String actionJson;

  private Boolean enabled;
  private Long createdBy;
}
