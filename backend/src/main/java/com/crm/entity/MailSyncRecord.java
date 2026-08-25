package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 邮件同步记录（062-email-sync）。 */
@Getter
@Setter
@TableName("mail_sync_record")
public class MailSyncRecord {

  private Long id;
  private Long accountId;

  /** INBOUND / OUTBOUND。 */
  private String direction;

  private String subject;
  private String fromAddress;
  private String toAddress;

  /** SYNCED / FAILED。 */
  private String syncStatus;

  private String externalId;
  private LocalDateTime syncTime;
}
