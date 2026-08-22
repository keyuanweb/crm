package com.crm.dto.customfield;

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
}
