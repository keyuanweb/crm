package com.crm.dto.customer;

import com.crm.dto.contact.ContactResponse;
import com.crm.dto.followup.FollowUpBrief;
import com.crm.dto.opportunity.OpportunityBrief;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 客户详情响应：基本信息 + 关联商机 + 跟进时间线 + 联系人（FR-002，005）+ 客户 360 聚合（018）。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerDetailResponse extends CustomerResponse {

  private List<OpportunityBrief> opportunities;
  private List<FollowUpBrief> followUps;
  private List<ContactResponse> contacts;

  /** 客户 360：订单/回款/合同/工单 + 金额汇总 + 健康度（018-customer-360）。 */
  private Customer360Response customer360;
}
