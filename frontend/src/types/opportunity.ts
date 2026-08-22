import type { CustomFieldValue } from './customField'

export interface Opportunity {
  id: number
  name: string
  customerId: number
  customerName?: string
  expectedAmountMin: number
  expectedAmountMax: number
  remark?: string
  status: 'ACTIVE' | 'ARCHIVED'
  salesOpportunityCount: number
  /** 自定义字段值（016）。 */
  customFieldValues?: CustomFieldValue[]
  version: number
  createdAt?: string
}

export type OpportunityStage = 'INITIAL_CONTACT' | 'NEGOTIATING' | 'CLOSED_WON' | 'CLOSED_LOST'
export type CloseResult = 'WON' | 'LOST'

export interface SalesOpportunity {
  id: number
  opportunityId: number
  opportunityName?: string
  customerName?: string
  amount: number
  stage: OpportunityStage
  expectedCloseDate?: string
  closeResult?: CloseResult
  closedAt?: string
  version: number
  createdAt?: string
}

export interface OpportunityDetail extends Opportunity {
  salesOpportunities: SalesOpportunity[]
}

export const STAGE_LABELS: Record<OpportunityStage, string> = {
  INITIAL_CONTACT: '初步接触',
  NEGOTIATING: '谈判中',
  CLOSED_WON: '已赢单',
  CLOSED_LOST: '已输单',
}

export const ACTIVE_STAGES: OpportunityStage[] = ['INITIAL_CONTACT', 'NEGOTIATING']

/** 金额（分）→ 元 */
export function formatAmount(amount: number | undefined | null): string {
  if (amount === undefined || amount === null) return '-'
  return (amount / 100).toLocaleString('zh-CN')
}
