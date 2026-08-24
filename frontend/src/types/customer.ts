import type { Contact } from './contact'
import type { CustomFieldValue } from './customField'

export interface Customer {
  id: number
  name: string
  company: string
  contactPerson?: string
  phone?: string
  email?: string
  address?: string
  remark?: string
  status: 'ACTIVE' | 'INACTIVE'
  ownerId?: number
  ownerName?: string
  /** 营销活动归因（014）。 */
  campaignId?: number
  /** 自定义字段值（016）。 */
  customFieldValues?: CustomFieldValue[]
  version: number
  createdAt?: string
}

export interface OpportunityBrief {
  id: number
  name: string
  status: string
  salesOpportunityCount: number
}

export interface FollowUpBrief {
  id: number
  method: string
  content: string
  createdAt: string
}

/** 客户 360 聚合（018-customer-360）。 */
export interface OrderBrief {
  id: number
  orderNo: string
  title: string
  amount: number
  status: string
}

export interface PaymentSummary {
  orderId: number
  orderNo: string
  totalPlan: number
  paid: number
  overdue: number
}

export interface ContractBrief {
  id: number
  contractNo: string
  title: string
  amount: number
  status: string
  startDate?: string
  endDate?: string
}

export interface TicketBrief {
  id: number
  title: string
  priority: string
  status: string
  slaStatus?: string
}

export interface AmountSummary {
  totalOrder: number
  paid: number
  dueOverdue: number
}

export interface ScoreDeduction {
  dimension: string
  deduct: number
}

export interface HealthScore {
  score: number
  level: 'RED' | 'YELLOW' | 'GREEN'
  deductions: ScoreDeduction[]
}

export interface Customer360 {
  orders: OrderBrief[]
  paymentSummaries: PaymentSummary[]
  contracts: ContractBrief[]
  tickets: TicketBrief[]
  amountSummary: AmountSummary
  health: HealthScore
}

export interface CustomerHealthBrief {
  id: number
  name: string
  company: string
  healthScore: number
  lastFollowUpAt?: string
  lastOrderAt?: string
  daysInactive: number
  ownerName?: string
}

export interface CustomerDetail extends Customer {
  opportunities: OpportunityBrief[]
  followUps: FollowUpBrief[]
  contacts: Contact[]
  customer360?: Customer360
}

export interface CustomerQuery {
  keyword?: string
  status?: string
  page: number
  pageSize: number
}
