package com.crm.dto.open;

import java.time.LocalDateTime;
import lombok.Data;

/** Webhook 订阅响应（创建时含 secret）。 */
@Data
public class WebhookResponse {

  private Long id;
  private String eventType;
  private String callbackUrl;

  /** 签名密钥（仅创建时返回）。 */
  private String secret;

  private Boolean enabled;
  private LocalDateTime createdAt;
}
