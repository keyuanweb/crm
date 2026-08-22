export type FieldEntityType = 'LEAD' | 'CUSTOMER' | 'OPPORTUNITY' | 'TICKET'
export type FieldType = 'TEXT' | 'TEXTAREA' | 'NUMBER' | 'DATE' | 'SELECT'

export interface CustomField {
  id: number
  entityType: FieldEntityType
  name: string
  fieldType: FieldType
  required: boolean
  options?: string
  enabled: boolean
  sortOrder?: number
  version: number
  createdAt?: string
}

export interface CustomFieldPayload {
  entityType: string
  name: string
  fieldType: string
  required?: boolean
  options?: string
  sortOrder?: number
  version?: number
}

export interface CustomFieldValue {
  fieldId: number
  fieldName?: string
  value?: string
}

export const FIELD_ENTITY_LABELS: Record<FieldEntityType, string> = {
  LEAD: '线索',
  CUSTOMER: '客户',
  OPPORTUNITY: '商机',
  TICKET: '工单',
}

export const FIELD_TYPE_LABELS: Record<FieldType, string> = {
  TEXT: '单行文本',
  TEXTAREA: '多行文本',
  NUMBER: '数字',
  DATE: '日期',
  SELECT: '下拉选择',
}
