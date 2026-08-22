package com.crm.dto.workflow;

import java.time.LocalDateTime;
import lombok.Data;

/** 站内通知响应。 */
@Data
public class NotificationResponse {

  private Long id;
  private String message;
  private Boolean read;
  private LocalDateTime createdAt;
}
