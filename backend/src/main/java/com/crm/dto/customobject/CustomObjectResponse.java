package com.crm.dto.customobject;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/** 自定义对象响应。 */
@Data
public class CustomObjectResponse {

  private Long id;
  private String name;
  private String code;
  private List<CustomObjectRequest.FieldDef> fields;
  private Boolean enabled;
  private Integer version;
  private LocalDateTime createdAt;
}
