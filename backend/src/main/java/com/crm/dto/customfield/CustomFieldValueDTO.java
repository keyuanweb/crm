package com.crm.dto.customfield;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 自定义字段值（请求/响应通用）。 */
@Data
public class CustomFieldValueDTO {

  @NotNull(message = "字段 id 不能为空")
  private Long fieldId;

  private String fieldName;

  @Size(max = 1000, message = "字段值不能超过 1000 字")
  private String value;
}
