package com.crm.dto.open;

import java.time.LocalDateTime;
import lombok.Data;

/** Webhook 推送记录响应。 */
@Data
public class DeliveryResponse {

  private Long id;
  private Long subscriptionId;
  private String eventType;
  private String entityType;
  private Long entityId;
  private String status;
  private Integer httpStatus;
  private String error;
  private Integer retryCount;
  private LocalDateTime createdAt;
}
