package com.crm.dto.playbook;

import java.time.LocalDateTime;
import lombok.Data;

/** 销售机会动作视图（含完成状态，FR-P03）。 */
@Data
public class ActionViewResponse {

  private Long templateId;
  private String actionName;
  private String description;
  private Boolean required;
  private Boolean completed;
  private Long completedBy;
  private LocalDateTime completedAt;
}
