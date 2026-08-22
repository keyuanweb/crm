package com.crm.dto.ticket;

import java.time.LocalDateTime;
import lombok.Data;

/** 工单回复响应。 */
@Data
public class TicketReplyResponse {

  private Long id;
  private Long ticketId;
  private Long replierId;
  private String replierName;
  private String content;
  private LocalDateTime createdAt;
}
