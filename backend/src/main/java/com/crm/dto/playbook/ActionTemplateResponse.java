package com.crm.dto.playbook;

import java.time.LocalDateTime;
import lombok.Data;

/** 阶段动作模板响应。 */
@Data
public class ActionTemplateResponse {

  private Long id;
  private String stage;
  private String actionName;
  private String description;
  private Integer sortOrder;
  private Boolean required;
  private Boolean enabled;
  private Integer version;
  private LocalDateTime createdAt;
}
