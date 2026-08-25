package com.crm.dto.portal;

import java.time.LocalDateTime;
import lombok.Data;

/** 门户提单响应。 */
@Data
public class PortalTicketResponse {

  private Long ticketId;
  private String status;
  private String priority;
  private LocalDateTime createdAt;
}
