package com.crm.dto.suggestion;

import lombok.Data;

/** 建议摘要（022-ai-assistant，FR-008 首页卡片）。 */
@Data
public class SuggestionSummary {

  private int atRiskCustomers;
  private int stalledOpportunities;
  private int followUpCustomers;
  private int highScoreLeads;
}
