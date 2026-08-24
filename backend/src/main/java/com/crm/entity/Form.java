package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 在线表单（036-online-forms）。 */
@Getter
@Setter
@TableName("form")
public class Form extends BaseEntity {

  private String name;

  /** JSON 字段配置。 */
  private String fields;

  private String successMessage;

  /** 线索来源（默认 WEBSITE）。 */
  private String source;

  /** ENABLED / DISABLED。 */
  private String status;

  private Integer submissionCount;
  private Long createdBy;
}
