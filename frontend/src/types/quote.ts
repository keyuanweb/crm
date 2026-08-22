export type QuoteStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED'

export interface QuoteItem {
  id: number
  productId?: number
  productName: string
  unitPrice: number
  quantity: number
  discount: number
  lineTotal: number
}

export interface Quote {
  id: number
  quoteNo: string
  customerId: number
  customerName?: string
  opportunityId?: number
  validUntil?: string
  status: QuoteStatus
  totalAmount: number
  remark?: string
  approverId?: number
  approvedAt?: string
  rejectReason?: string
  items?: QuoteItem[]
  version: number
  createdAt?: string
}

export interface QuoteItemPayload {
  productId: number
  quantity: number
  discount: number
}

export interface QuotePayload {
  customerId: number
  opportunityId?: number
  validUntil?: string
  remark?: string
  items: QuoteItemPayload[]
  version?: number
}

export interface QuoteListParams {
  keyword?: string
  status?: string
  customerId?: number
  page: number
  pageSize: number
}

export const QUOTE_STATUS_LABELS: Record<QuoteStatus, string> = {
  DRAFT: '草稿',
  PENDING_APPROVAL: '待审批',
  APPROVED: '已通过',
  REJECTED: '已拒绝',
}

export const QUOTE_STATUS_COLORS: Record<QuoteStatus, string> = {
  DRAFT: 'default',
  PENDING_APPROVAL: 'processing',
  APPROVED: 'success',
  REJECTED: 'error',
}
