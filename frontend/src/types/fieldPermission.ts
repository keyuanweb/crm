export interface FieldPermission {
  id: number
  roleCode: string
  entityType: string
  fieldId: number
  fieldName?: string
  permission: 'HIDDEN' | 'READ_ONLY' | 'EDITABLE'
  createdAt?: string
}

export const FIELD_PERMISSION_LABELS: Record<string, string> = {
  HIDDEN: '隐藏',
  READ_ONLY: '只读',
  EDITABLE: '可编辑',
}

export const FIELD_ENTITY_LABELS: Record<string, string> = {
  LEAD: '线索',
  CUSTOMER: '客户',
  OPPORTUNITY: '商机',
  TICKET: '工单',
}
