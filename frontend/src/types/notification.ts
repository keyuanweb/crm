export type NotificationType = 'WORKFLOW' | 'TICKET_ASSIGN' | 'TICKET_REPLY'

export interface Notification {
  id: number
  type: NotificationType
  message: string
  read: boolean
  entityType?: string
  entityId?: number
  createdAt?: string
}

export const NOTIFICATION_TYPE_LABELS: Record<NotificationType, string> = {
  WORKFLOW: '工作流',
  TICKET_ASSIGN: '工单分配',
  TICKET_REPLY: '工单回复',
}
