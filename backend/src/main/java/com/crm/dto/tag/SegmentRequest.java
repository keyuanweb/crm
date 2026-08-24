package com.crm.dto.tag;

import lombok.Data;

/** 细分请求（031），conditions 为 JSON 字符串。 */
@Data
public class SegmentRequest {

  private String name;
  private String description;

  /** JSON: { "logic":"AND", "filters":[ { "field":"tag", "op":"IN", "values":["VIP"] } ] } */
  private String conditions;
}
