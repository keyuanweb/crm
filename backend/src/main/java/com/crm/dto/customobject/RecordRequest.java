package com.crm.dto.customobject;

import java.util.Map;
import lombok.Data;

/** 对象记录请求。 */
@Data
public class RecordRequest {

  private Map<String, Object> values;
}
