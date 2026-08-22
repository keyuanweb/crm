package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 工作流执行日志（013-workflow-automation）。 */
@Getter
@Setter
@TableName("workflow_execution_log")
public class WorkflowExecutionLog extends BaseEntity {

  private Long ruleId;
  private String eventType;
  private String entityType;
  private Long entityId;
  private Boolean matched;
  private String actionResult;
  private Boolean success;
  private String errorMessage;
}
