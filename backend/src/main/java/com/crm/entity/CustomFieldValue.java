package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 自定义字段值（016-system-enhancement，字符串存储）。 */
@Getter
@Setter
@TableName("custom_field_value")
public class CustomFieldValue {

  private Long id;
  private Long fieldId;
  private String entityType;
  private Long entityId;
  private String fieldValue;
  private LocalDateTime createdAt;
}
