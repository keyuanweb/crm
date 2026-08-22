package com.crm.dto.workflow;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;

/** 工作流规则响应。 */
@Data
public class WorkflowRuleResponse {

  private Long id;
  private String name;
  private String eventType;
  private Map<String, String> condition;
  private String actionType;
  private Map<String, Object> action;
  private Boolean enabled;
  private Integer version;
  private LocalDateTime createdAt;
}
