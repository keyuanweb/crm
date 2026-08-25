package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 邮件账户（062-email-sync）。 */
@Getter
@Setter
@TableName("mail_account")
public class MailAccount {

  private Long id;
  private String email;
  private String displayName;
  private String imapHost;
  private Integer imapPort;
  private String smtpHost;
  private Integer smtpPort;
  private Integer enabled;
  private Integer isDefaultSender;
  private Long createdBy;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
