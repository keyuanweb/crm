package com.crm.dto.tag;

import lombok.Data;

/** 标签创建/编辑请求（031）。 */
@Data
public class TagRequest {

  private String name;
  private String color;
  private String entityType;
}
