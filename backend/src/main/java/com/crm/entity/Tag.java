package com.crm.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 标签（031-customer-tags）。 */
@Getter
@Setter
@TableName("tag")
public class Tag extends BaseEntity {

  private String name;
  private String color;

  /** CUSTOMER / LEAD / CONTACT。 */
  private String entityType;

  @TableField(fill = FieldFill.INSERT)
  private Long createdBy;
}
