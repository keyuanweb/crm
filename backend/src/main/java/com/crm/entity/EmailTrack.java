package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/** 邮件打开/点击事件（030-email-marketing）。 */
@Getter
@Setter
@TableName("email_track")
public class EmailTrack {

  private Long id;
  private Long sendLogId;

  /** OPEN / CLICK。 */
  private String trackType;

  private String clickUrl;
  private java.time.LocalDateTime createdAt;
}
