package com.crm.dto.ticket;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 工单回复请求（FR-C04）。 */
@Data
public class TicketReplyRequest {

  @NotBlank(message = "回复内容不能为空")
  private String content;
}
