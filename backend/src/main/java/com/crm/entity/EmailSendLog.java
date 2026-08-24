package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 邮件发送记录（030-email-marketing）。 */
@Getter
@Setter
@TableName("email_send_log")
public class EmailSendLog {

  private Long id;
  private Long campaignId;
  private Long customerId;
  private String email;
  private String subject;
  private String content;

  /** SENT / FAILED。 */
  private String status;

  private String errorMessage;
  private java.time.LocalDateTime createdAt;
}
