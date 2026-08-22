package com.crm.dto.lead;

import com.crm.dto.followup.FollowUpBrief;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class LeadDetailResponse extends LeadResponse {

  private List<FollowUpBrief> followUps;
}
