package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Webhook 订阅（055）。 */
@Getter
@Setter
@TableName("webhook_subscription")
public class WebhookSubscription {

  private Long id;
  private String eventType;
  private String callbackUrl;
  private String secret;
  private Integer enabled;
  private Long createdBy;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
