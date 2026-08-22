package com.crm.dto.contract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 合同模板创建/编辑请求（FR-CT08）。 */
@Data
public class ContractTemplateRequest {

  @NotBlank(message = "模板名称不能为空")
  @Size(max = 100, message = "名称不能超过 100 字")
  private String name;

  @NotBlank(message = "模板正文不能为空")
  private String content;

  private String status;

  private Integer version;
}
