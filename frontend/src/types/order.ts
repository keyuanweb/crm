export type OrderStatus = 'PENDING' | 'PARTIAL' | 'PAID'

export type PlanStatus = 'PENDING' | 'PARTIAL' | 'PAID'

export type ReminderStatus = 'PAID' | 'NORMAL' | 'DUE_SOON' | 'OVERDUE'

export interface PaymentPlanItem {
  id: number
  seqNo: number
  amount: number
  dueDate: string
  description?: string
  status: PlanStatus
  receivedAmount: number
  unpaidAmount: number
  reminderStatus: ReminderStatus
  overdueDays?: number
}

export interface PaymentRecord {
  id: number
  planId: number
  amount: number
  paidAt: string
  method: string
  recordedBy?: number
  createdAt?: string
}

export interface Order {
  id: number
  orderNo: string
  title: string
  customerId: number
  customerName?: string
  contractId?: number
  amount: number
  status: OrderStatus
  paidAmount?: number
  description?: string
  plans?: PaymentPlanItem[]
  payments?: PaymentRecord[]
  version: number
  createdAt?: string
}

export interface PlanItemPayload {
  amount: number
  dueDate: string
  description?: string
}

export interface OrderPayload {
  title: string
  customerId: number
  contractId?: number
  amount?: number
  description?: string
  plans?: PlanItemPayload[]
  version?: number
}

export interface PaymentPayload {
  planId: number
  amount: number
  paidAt: string
  method: string
}

export interface OrderListParams {
  keyword?: string
  status?: string
  customerId?: number
  page: number
  pageSize: number
}

export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  PENDING: '待回款',
  PARTIAL: '部分回款',
  PAID: '已结清',
}

export const ORDER_STATUS_COLORS: Record<OrderStatus, string> = {
  PENDING: 'default',
  PARTIAL: 'processing',
  PAID: 'success',
}

export const REMINDER_LABELS: Record<ReminderStatus, string> = {
  PAID: '已回款',
  NORMAL: '正常',
  DUE_SOON: '临期',
  OVERDUE: '逾期',
}

export const REMINDER_COLORS: Record<ReminderStatus, string> = {
  PAID: 'green',
  NORMAL: 'default',
  DUE_SOON: 'gold',
  OVERDUE: 'red',
}

export const PAYMENT_METHOD_LABELS: Record<string, string> = {
  TRANSFER: '转账',
  CASH: '现金',
  CHECK: '支票',
  OTHER: '其他',
}
