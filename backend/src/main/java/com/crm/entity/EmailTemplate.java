package com.crm.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 邮件模板（030-email-marketing）。 */
@Getter
@Setter
@TableName("email_template")
public class EmailTemplate extends BaseEntity {

  private String name;
  private String subject;
  private String content;

  /** WELCOME / PROMOTION / FOLLOW_UP / NOTICE。 */
  private String category;

  @TableField(fill = FieldFill.INSERT)
  private Long createdBy;
}
