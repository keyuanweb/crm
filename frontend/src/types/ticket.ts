export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'
export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED'
export type TicketSlaStatus = 'NORMAL' | 'WARNING' | 'OVERDUE'

import type { CustomFieldValue } from './customField'

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
  /** 自定义字段值（016）。 */
  customFieldValues?: CustomFieldValue[]
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
  /** 自定义字段值（016）。 */
  customFieldValues?: { fieldId: number; value?: string }[]
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

export const TICKET_STATUS_COLORS: Record<TicketStatus, string> = {
  OPEN: 'red',
  IN_PROGRESS: 'processing',
  RESOLVED: 'blue',
  CLOSED: 'default',
}

export const TICKET_PRIORITY_COLORS: Record<TicketPriority, string> = {
  LOW: 'default',
  MEDIUM: 'blue',
  HIGH: 'orange',
  URGENT: 'red',
}

export const TICKET_SLA_COLORS: Record<TicketSlaStatus, string> = {
  NORMAL: 'green',
  WARNING: 'gold',
  OVERDUE: 'red',
}
