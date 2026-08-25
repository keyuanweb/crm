package com.crm.dto.integration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 集成通道请求。 */
@Data
public class ChannelRequest {

  @NotBlank(message = "通道类型不能为空")
  @Pattern(regexp = "^(WECHAT_WORK|DINGTALK|CUSTOM)$", message = "通道类型不合法")
  private String channelType;

  @NotBlank(message = "通道名称不能为空")
  @Size(max = 100, message = "名称不能超过 100 字")
  private String name;

  @NotBlank(message = "Webhook URL 不能为空")
  @Size(max = 500, message = "URL 不能超过 500 字")
  private String webhookUrl;

  private Boolean enabled;
}
