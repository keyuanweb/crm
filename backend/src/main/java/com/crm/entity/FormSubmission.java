package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 表单提交快照（036-online-forms）。 */
@Getter
@Setter
@TableName("form_submission")
public class FormSubmission {

  private Long id;
  private Long formId;
  private String payload;
  private String clientIp;
  private Long leadId;
  private java.time.LocalDateTime createdAt;
}
