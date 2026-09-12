export type NotificationType =
  | 'WORKFLOW'
  | 'TICKET_ASSIGN'
  | 'TICKET_REPLY'
  | 'SLA_WARNING'
  | 'SLA_OVERDUE'

export interface Notification {
  id: number
  type: NotificationType
  message: string
  read: boolean
  entityType?: string
  entityId?: number
  createdAt?: string
}
