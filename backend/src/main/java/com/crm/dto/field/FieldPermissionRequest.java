package com.crm.dto.field;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 字段权限配置请求。 */
@Data
public class FieldPermissionRequest {

  @NotNull(message = "角色不能为空")
  private String roleCode;

  @NotBlank(message = "实体类型不能为空")
  private String entityType;

  @NotNull(message = "字段不能为空")
  private Long fieldId;

  @NotBlank(message = "权限值不能为空")
  @Pattern(regexp = "^(HIDDEN|READ_ONLY|EDITABLE)$", message = "权限值不合法")
  private String permission;
}
