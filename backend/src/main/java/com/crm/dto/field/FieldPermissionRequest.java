package com.crm.dto.field;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 字段权限配置请求。
 *
 * <p><b>102 起 {@code fieldId} 与 {@code fieldKey} 二选一</b>：自定义字段用 {@code fieldId}，内置字段用 {@code
 * fieldKey}（属性名）。「恰好一个非空」这条跨字段规则 Bean Validation 表达不了，由 {@code FieldPermissionService.upsert} 强制（违反
 * ⇒ 422 {@code FIELD_PERMISSION_INVALID}）。故 {@code fieldId} 上不再有 {@code @NotNull}——留着它会先一步把合法请求拦掉。
 */
@Data
public class FieldPermissionRequest {

  @NotNull(message = "角色不能为空")
  private String roleCode;

  @NotBlank(message = "实体类型不能为空")
  private String entityType;

  /** 自定义字段 id；内置字段配置时留空。 */
  private Long fieldId;

  /** 内置字段名（属性名，如 {@code phone}）；自定义字段配置时留空。 */
  @Size(max = 64, message = "内置字段名过长")
  private String fieldKey;

  @NotBlank(message = "权限值不能为空")
  @Pattern(regexp = "^(HIDDEN|READ_ONLY|EDITABLE)$", message = "权限值不合法")
  private String permission;
}
