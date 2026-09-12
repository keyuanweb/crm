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

  /** 已真正发出。 */
  public static final String STATUS_SENT = "SENT";

  /** 发送抛异常。 */
  public static final String STATUS_FAILED = "FAILED";

  /** 未配置 SMTP，未发送（区别于 SENT；一期诚信修复新增）。 */
  public static final String STATUS_SKIPPED = "SKIPPED";

  /** 待发送（已入库、尚未尝试）。 */
  public static final String STATUS_PENDING = "PENDING";

  /** SENT / FAILED / SKIPPED / PENDING。 */
  private String status;

  /** 052：该封邮件所属变体（A/B）。 */
  private String variant;

  private String errorMessage;
  private java.time.LocalDateTime createdAt;
}
