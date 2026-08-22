package com.crm.dto.opportunity;

import lombok.Data;

/** 客户详情中的商机摘要。 */
@Data
public class OpportunityBrief {

  private Long id;
  private String name;
  private String status;
  private Integer salesOpportunityCount;
}
