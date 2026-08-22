package com.crm.dto.opportunity;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 商机详情响应：基本信息 + 下属销售机会（FR-008）。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OpportunityDetailResponse extends OpportunityResponse {

  private List<SalesOpportunityResponse> salesOpportunities;
}
