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

  /** 真实收信落库（同步成功）。 */
  public static final String STATUS_SYNCED = "SYNCED";

  /** 同步失败。 */
  public static final String STATUS_FAILED = "FAILED";

  /** 演示数据，不是真实收信（101 收信侧诚实化新增；只在显式打开 demo 开关时产生）。 */
  public static final String STATUS_SIMULATED = "SIMULATED";

  /** SYNCED / FAILED / SIMULATED。值域由本类常量维护，V69 的 COMMENT 不追（030 的 SKIPPED 同此处置）。 */
  private String syncStatus;

  private String externalId;
  private LocalDateTime syncTime;
}
