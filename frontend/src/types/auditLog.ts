export type AuditAction =
  | 'CREATE'
  | 'UPDATE'
  | 'DELETE'
  | 'IMPORT'
  | 'EXPORT'
  | 'CLOSE'
  | 'RESET_PASSWORD'
  | 'CHANGE_PASSWORD'

export type AuditEntityType = 'CUSTOMER' | 'OPPORTUNITY' | 'SALES_OPPORTUNITY' | 'USER'

export interface AuditLog {
  id: number
  actorId?: number
  actorName?: string
  action: AuditAction
  entityType: AuditEntityType
  entityId?: number
  detail?: string
  createdAt: string
}
