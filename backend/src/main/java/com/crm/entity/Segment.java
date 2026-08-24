package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 动态细分（031-customer-tags），conditions 为 JSON。 */
@Getter
@Setter
@TableName("segment")
public class Segment extends BaseEntity {

  private String name;
  private String description;
  private String conditions;
  private Long createdBy;
}
