package com.crm.ws;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** WebSocket 通知推送消息（026-realtime-notify）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPushPayload {

  private Long id;
  private String type;
  private String message;
  private long unreadCount;
}
