package com.crm.dto.customer;

import com.crm.dto.followup.FollowUpBrief;
import com.crm.dto.opportunity.OpportunityBrief;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 客户详情响应：基本信息 + 关联商机 + 跟进时间线（FR-002）。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerDetailResponse extends CustomerResponse {

  private List<OpportunityBrief> opportunities;
  private List<FollowUpBrief> followUps;
}
