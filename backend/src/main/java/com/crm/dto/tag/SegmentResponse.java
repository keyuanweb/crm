package com.crm.dto.tag;

import lombok.Data;

/** 细分响应（031）。 */
@Data
public class SegmentResponse {

  private Long id;
  private String name;
  private String description;
  private String conditions;
  private long memberCount;
}
