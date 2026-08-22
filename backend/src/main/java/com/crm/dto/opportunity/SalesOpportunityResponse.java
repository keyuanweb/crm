package com.crm.dto.opportunity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/** 销售机会响应。 */
@Data
public class SalesOpportunityResponse {

  private Long id;
  private Long opportunityId;
  private String opportunityName;
  private String customerName;
  private Long amount;
  private String stage;
  private LocalDate expectedCloseDate;
  private String closeResult;
  private LocalDateTime closedAt;
  private Integer version;
  private LocalDateTime createdAt;
}
