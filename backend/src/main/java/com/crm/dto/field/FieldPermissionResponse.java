package com.crm.dto.field;

import java.time.LocalDateTime;
import lombok.Data;

/** 字段权限配置响应。 */
@Data
public class FieldPermissionResponse {

  private Long id;
  private String roleCode;
  private String entityType;
  private Long fieldId;

  /** 内置字段名（属性名）；自定义字段配置为 null（102）。 */
  private String fieldKey;

  private String fieldName;
  private String permission;
  private LocalDateTime createdAt;
}
