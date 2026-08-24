package com.crm.dto.suggestion;

import lombok.Data;

/** 智能建议（022-ai-assistant，FR-001/002/003）。 */
@Data
public class SmartSuggestion {

  /** CUSTOMER_AT_RISK / OPPORTUNITY_STALLED / CUSTOMER_FOLLOWUP / LEAD_HIGH_SCORE。 */
  private String type;

  private String title;
  private String reason;

  /** URGENT / IMPORTANT / NORMAL。 */
  private String priority;

  /** CUSTOMER / OPPORTUNITY / LEAD。 */
  private String entityType;

  private Long entityId;

  /** follow_up / push / process。 */
  private String action;

  public static final String TYPE_AT_RISK = "CUSTOMER_AT_RISK";
  public static final String TYPE_STALLED = "OPPORTUNITY_STALLED";
  public static final String TYPE_FOLLOWUP = "CUSTOMER_FOLLOWUP";
  public static final String TYPE_HIGH_SCORE_LEAD = "LEAD_HIGH_SCORE";
}
