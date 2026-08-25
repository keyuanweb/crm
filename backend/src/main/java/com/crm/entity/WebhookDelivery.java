package com.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Webhook 推送记录（055）。 */
@Getter
@Setter
@TableName("webhook_delivery")
public class WebhookDelivery {

  private Long id;
  private Long subscriptionId;
  private String eventType;
  private String entityType;
  private Long entityId;
  private String payload;

  /** SUCCESS / FAILED。 */
  private String status;

  private Integer httpStatus;
  private String error;
  private Integer retryCount;
  private LocalDateTime createdAt;
}
