package com.crm.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 字段级权限（056-field-permission，按角色编码）。 */
@Getter
@Setter
@TableName("field_permission")
public class FieldPermission {

  private Long id;

  /** 角色编码（ADMIN/SALES/SUPPORT 等）。 */
  private String roleCode;

  private String entityType;

  /** 自定义字段 id；**内置字段行为 NULL**（102 起本列可空，见 {@code V91}）。 */
  private Long fieldId;

  /**
   * 内置字段名（属性名，如 {@code phone}）；**自定义字段行为 NULL**（102）。
   *
   * <p>与 {@link #fieldId} **二选一**（「恰好一列非空」由 {@code FieldPermissionService.upsert} 保证，库里没有约束）。
   */
  private String fieldKey;

  /** HIDDEN / READ_ONLY / EDITABLE。 */
  private String permission;

  @TableField(fill = FieldFill.INSERT)
  private Long createdBy;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
