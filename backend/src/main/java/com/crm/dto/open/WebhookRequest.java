package com.crm.dto.open;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Webhook 订阅请求。 */
@Data
public class WebhookRequest {

  @NotBlank(message = "事件类型不能为空")
  @Pattern(
      regexp = "^(LEAD_CREATED|LEAD_UPDATED|CUSTOMER_CREATED)$",
      message = "事件类型不合法")
  private String eventType;

  @NotBlank(message = "回调 URL 不能为空")
  @Size(max = 500, message = "回调 URL 不能超过 500 字")
  private String callbackUrl;
}
