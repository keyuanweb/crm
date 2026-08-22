package com.crm.dto.opportunity;

import java.time.LocalDateTime;
import lombok.Data;

/** 商机列表项响应。 */
@Data
public class OpportunityResponse {

  private Long id;
  private String name;
  private Long customerId;
  private String customerName;
  private Long expectedAmountMin;
  private Long expectedAmountMax;
  private String remark;
  private String status;
  private Integer salesOpportunityCount;
  private Integer version;
  private LocalDateTime createdAt;
}
