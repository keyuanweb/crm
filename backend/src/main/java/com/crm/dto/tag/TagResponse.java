package com.crm.dto.tag;

import lombok.Data;

/** 标签响应（031）。 */
@Data
public class TagResponse {

  private Long id;
  private String name;
  private String color;
  private String entityType;
}
