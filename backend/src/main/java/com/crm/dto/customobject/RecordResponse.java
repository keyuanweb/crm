package com.crm.dto.customobject;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;

/** 对象记录响应。 */
@Data
public class RecordResponse {

  private Long id;
  private Long objectId;
  private Map<String, Object> values;
  private Long createdBy;
  private LocalDateTime createdAt;
}
