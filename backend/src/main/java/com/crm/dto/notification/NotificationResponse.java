package com.crm.dto.notification;

import java.time.LocalDateTime;
import lombok.Data;

/** 通知响应。 */
@Data
public class NotificationResponse {

  private Long id;
  private String type;
  private String message;
  private Boolean read;
  private String entityType;
  private Long entityId;
  private LocalDateTime createdAt;
}
