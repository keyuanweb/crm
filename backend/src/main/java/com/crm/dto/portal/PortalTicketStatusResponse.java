package com.crm.dto.portal;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/** 门户工单进度响应。 */
@Data
public class PortalTicketStatusResponse {

  private Long ticketId;
  private String status;
  private String priority;
  private String slaStatus;
  private LocalDateTime createdAt;
  private List<ReplyView> replies;

  @Data
  public static class ReplyView {
    private String content;
    private LocalDateTime createdAt;
  }
}
