package com.crm.dto.customfield;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 自定义字段定义请求（FR-S01）。 */
@Data
public class CustomFieldRequest {

  @NotBlank(message = "适用实体不能为空")
  @Pattern(regexp = "^(LEAD|CUSTOMER|OPPORTUNITY|TICKET)$", message = "适用实体不合法")
  private String entityType;

  @NotBlank(message = "字段名称不能为空")
  @Size(max = 50, message = "字段名称不能超过 50 字")
  private String name;

  @NotBlank(message = "字段类型不能为空")
  @Pattern(regexp = "^(TEXT|TEXTAREA|NUMBER|DATE|SELECT)$", message = "字段类型不合法")
  private String fieldType;

  private Boolean required;

  @Size(max = 1000, message = "选项不能超过 1000 字")
  private String options;

  private Integer sortOrder;

  private Integer version;
}
