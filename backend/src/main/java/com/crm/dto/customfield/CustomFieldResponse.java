package com.crm.dto.customfield;

import com.crm.dto.field.FieldPermissionView;
import java.time.LocalDateTime;
import lombok.Data;

/** 自定义字段定义响应。 */
@Data
public class CustomFieldResponse {

  private Long id;
  private String entityType;
  private String name;
  private String fieldType;
  private Boolean required;
  private String options;
  private Boolean enabled;
  private Integer sortOrder;
  private Integer version;
  private LocalDateTime createdAt;

  /** 056：当前角色字段权限（hidden/readOnly）。 */
  private FieldPermissionView permission;
}
