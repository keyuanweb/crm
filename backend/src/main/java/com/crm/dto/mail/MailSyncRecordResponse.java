package com.crm.dto.mail;

import java.time.LocalDateTime;
import lombok.Data;

/** 邮件同步记录响应。 */
@Data
public class MailSyncRecordResponse {

  private Long id;
  private Long accountId;
  private String direction;
  private String subject;
  private String fromAddress;
  private String toAddress;
  private String syncStatus;
  private String externalId;
  private LocalDateTime syncTime;
}
