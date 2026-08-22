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

export const ACTION_LABELS: Record<AuditAction, string> = {
  CREATE: '创建',
  UPDATE: '编辑',
  DELETE: '删除',
  IMPORT: '导入',
  EXPORT: '导出',
  CLOSE: '关闭',
  RESET_PASSWORD: '重置密码',
  CHANGE_PASSWORD: '修改密码',
}

export const ENTITY_LABELS: Record<AuditEntityType, string> = {
  CUSTOMER: '客户',
  OPPORTUNITY: '商机',
  SALES_OPPORTUNITY: '销售机会',
  USER: '用户',
}
