package com.crm.dto.ticket;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 工单分配请求（FR-C05）。 */
@Data
public class TicketAssignRequest {

  @NotNull(message = "处理人不能为空")
  private Long assigneeId;
}
