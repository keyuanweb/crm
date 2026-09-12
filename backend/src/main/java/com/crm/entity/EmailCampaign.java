package com.crm.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 邮件群发批次（030-email-marketing）。 */
@Getter
@Setter
@TableName("email_campaign")
public class EmailCampaign extends BaseEntity {

  private Long templateId;
  private String name;

  /** SEGMENT / CUSTOMER_IDS。 */
  private String sourceType;

  private String sourceRef;
  private int totalCount;
  private int sentCount;
  private int failedCount;
  private int openCount;
  private int clickCount;

  public static final String STATUS_PENDING = "PENDING";
  public static final String STATUS_RUNNING = "RUNNING";

  /** 该批次全部发送尝试已结束。 */
  public static final String STATUS_DONE = "DONE";

  public static final String STATUS_FAILED = "FAILED";

  /** 未配置 SMTP，本批次邮件未发送（区别于真正发完的 DONE；一期诚信修复新增）。 */
  public static final String STATUS_SKIPPED = "SKIPPED";

  /** PENDING / RUNNING / DONE / FAILED / SKIPPED。 */
  private String status;

  /** 052：A/B 测试（NONE/A/B）+ B 主题 + 更优者标记。 */
  private String variant;

  private String subjectB;
  private String winner;

  @TableField(fill = FieldFill.INSERT)
  private Long createdBy;
}
