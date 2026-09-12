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
  private Long fieldId;

  /** HIDDEN / READ_ONLY / EDITABLE。 */
  private String permission;

  @TableField(fill = FieldFill.INSERT)
  private Long createdBy;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
