package com.crm.dto.ticket;

import java.time.LocalDateTime;
import lombok.Data;

/** 工单响应（含 SLA 与回复数）。 */
@Data
public class TicketResponse {

  private Long id;
  private Long customerId;
  private String customerName;
  private Long contactId;
  private String contactName;
  private String title;
  private String description;
  private String priority;
  private String status;
  private Long assigneeId;
  private String assigneeName;
  private LocalDateTime slaRespondDeadline;
  private LocalDateTime slaResolveDeadline;
  private String slaStatus;
  private Long replyCount;
  private String remark;
  private Integer version;
  private LocalDateTime createdAt;
}
