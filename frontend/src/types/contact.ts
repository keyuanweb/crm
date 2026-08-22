export type ContactRole =
  | 'DECISION_MAKER'
  | 'INFLUENCER'
  | 'EVALUATOR'
  | 'CHAMPION'
  | 'OTHER'

export interface Contact {
  id: number
  customerId: number
  customerName?: string
  name: string
  title?: string
  phone?: string
  email?: string
  role: ContactRole
  remark?: string
  version: number
  createdAt?: string
}

export interface ContactPayload {
  customerId: number
  name: string
  title?: string
  phone?: string
  email?: string
  role?: string
  remark?: string
  version?: number
}

export interface ContactListParams {
  keyword?: string
  customerId?: number
  role?: string
  page: number
  pageSize: number
}

export const ROLE_LABELS: Record<ContactRole, string> = {
  DECISION_MAKER: '决策人',
  INFLUENCER: '影响者',
  EVALUATOR: '评估人',
  CHAMPION: '支持者',
  OTHER: '其他',
}

export const ROLE_COLORS: Record<ContactRole, string> = {
  DECISION_MAKER: 'red',
  INFLUENCER: 'blue',
  EVALUATOR: 'gold',
  CHAMPION: 'green',
  OTHER: 'default',
}
