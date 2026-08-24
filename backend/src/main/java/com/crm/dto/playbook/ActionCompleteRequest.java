package com.crm.dto.playbook;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 销售机会动作勾选完成请求（FR-P04）。 */
@Data
public class ActionCompleteRequest {

  @NotNull(message = "模板 id 不能为空")
  private Long templateId;
}
