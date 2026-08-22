package com.crm.dto.workflow;

import java.time.LocalDateTime;
import lombok.Data;

/** 工作流执行日志响应。 */
@Data
public class ExecutionLogResponse {

  private Long id;
  private Long ruleId;
  private String ruleName;
  private String eventType;
  private String entityType;
  private Long entityId;
  private Boolean matched;
  private String actionResult;
  private Boolean success;
  private String errorMessage;
  private LocalDateTime createdAt;
}
