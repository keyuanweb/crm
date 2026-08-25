package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 邮件退订（052-email-advanced）。 */
@Getter
@Setter
@TableName("email_unsubscribe")
public class EmailUnsubscribe {

  private Long id;
  private String email;
  private Long campaignId;
  private LocalDateTime unsubscribedAt;
}
