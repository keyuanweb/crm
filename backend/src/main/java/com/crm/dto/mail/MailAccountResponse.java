package com.crm.dto.mail;

import java.time.LocalDateTime;
import lombok.Data;

/** 邮件账户响应。 */
@Data
public class MailAccountResponse {

  private Long id;
  private String email;
  private String displayName;
  private String imapHost;
  private Integer imapPort;
  private String smtpHost;
  private Integer smtpPort;
  private Boolean enabled;
  private Boolean isDefaultSender;
  private LocalDateTime createdAt;
}
