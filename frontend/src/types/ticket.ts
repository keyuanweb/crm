export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'
export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED'
export type TicketSlaStatus = 'NORMAL' | 'WARNING' | 'OVERDUE'

export interface Ticket {
  id: number
  customerId: number
  customerName?: string
  contactId?: number
  contactName?: string
  title: string
  description?: string
  priority: TicketPriority
  status: TicketStatus
  assigneeId?: number
  assigneeName?: string
  slaRespondDeadline?: string
  slaResolveDeadline?: string
  slaStatus?: TicketSlaStatus
  replyCount?: number
  remark?: string
  version: number
  createdAt?: string
}

export interface TicketPayload {
  customerId: number
  contactId?: number
  title: string
  description?: string
  priority: string
  assigneeId?: number
  remark?: string
  version?: number
}

export interface TicketListParams {
  keyword?: string
  status?: string
  priority?: string
  assigneeId?: number
  customerId?: number
  page: number
  pageSize: number
}

export interface TicketReply {
  id: number
  ticketId: number
  replierId?: number
  replierName?: string
  content: string
  createdAt?: string
}

export const TICKET_STATUS_LABELS: Record<TicketStatus, string> = {
  OPEN: '待处理',
  IN_PROGRESS: '处理中',
  RESOLVED: '已解决',
  CLOSED: '已关闭',
}

export const TICKET_STATUS_COLORS: Record<TicketStatus, string> = {
  OPEN: 'red',
  IN_PROGRESS: 'processing',
  RESOLVED: 'blue',
  CLOSED: 'default',
}

export const TICKET_PRIORITY_LABELS: Record<TicketPriority, string> = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
  URGENT: '紧急',
}

export const TICKET_PRIORITY_COLORS: Record<TicketPriority, string> = {
  LOW: 'default',
  MEDIUM: 'blue',
  HIGH: 'orange',
  URGENT: 'red',
}

export const TICKET_SLA_LABELS: Record<TicketSlaStatus, string> = {
  NORMAL: '正常',
  WARNING: '即将超时',
  OVERDUE: '已超时',
}

export const TICKET_SLA_COLORS: Record<TicketSlaStatus, string> = {
  NORMAL: 'green',
  WARNING: 'gold',
  OVERDUE: 'red',
}
