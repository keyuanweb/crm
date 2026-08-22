package com.crm.dto.ticket;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 工单状态流转请求（FR-C03）。 */
@Data
public class TicketTransitionRequest {

  @NotBlank(message = "目标状态不能为空")
  @Pattern(regexp = "^(IN_PROGRESS|RESOLVED|CLOSED)$", message = "目标状态不合法")
  private String targetStatus;
}
