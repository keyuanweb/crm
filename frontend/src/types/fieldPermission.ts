export interface FieldPermission {
  id: number
  roleCode: string
  entityType: string
  fieldId: number
  fieldName?: string
  permission: 'HIDDEN' | 'READ_ONLY' | 'EDITABLE'
  createdAt?: string
}
