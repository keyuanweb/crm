/** 智能建议（022）。 */
export type SuggestionType =
  | 'CUSTOMER_AT_RISK'
  | 'OPPORTUNITY_STALLED'
  | 'CUSTOMER_FOLLOWUP'
  | 'LEAD_HIGH_SCORE'

export interface SmartSuggestion {
  type: SuggestionType
  title: string
  reason: string
  priority: 'URGENT' | 'IMPORTANT' | 'NORMAL'
  entityType: 'CUSTOMER' | 'OPPORTUNITY' | 'LEAD'
  entityId: number
  action: string
}

export interface SuggestionSummary {
  atRiskCustomers: number
  stalledOpportunities: number
  followUpCustomers: number
  highScoreLeads: number
}
