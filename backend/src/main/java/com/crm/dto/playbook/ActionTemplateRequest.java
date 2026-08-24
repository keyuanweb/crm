package com.crm.dto.playbook;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 阶段动作模板请求（FR-P01）。 */
@Data
public class ActionTemplateRequest {

  @NotBlank(message = "阶段不能为空")
  @Pattern(regexp = "^(INITIAL_CONTACT|NEGOTIATING)$", message = "仅活动阶段可配置动作")
  private String stage;

  @NotBlank(message = "动作名称不能为空")
  @Size(max = 100, message = "动作名称不能超过 100 字")
  private String actionName;

  @Size(max = 500, message = "描述不能超过 500 字")
  private String description;

  private Integer sortOrder;

  private Boolean required;

  private Integer version;
}
