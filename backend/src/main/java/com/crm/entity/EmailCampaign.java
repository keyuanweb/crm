package com.crm.entity;

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

  /** PENDING / RUNNING / DONE / FAILED。 */
  private String status;

  /** 052：A/B 测试（NONE/A/B）+ B 主题 + 更优者标记。 */
  private String variant;

  private String subjectB;
  private String winner;

  private Long createdBy;
}
