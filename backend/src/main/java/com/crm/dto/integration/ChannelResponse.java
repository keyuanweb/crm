package com.crm.dto.integration;

import java.time.LocalDateTime;
import lombok.Data;

/** 集成通道响应。 */
@Data
public class ChannelResponse {

  private Long id;
  private String channelType;
  private String name;
  private String webhookUrl;
  private Boolean enabled;
  private LocalDateTime createdAt;
}
