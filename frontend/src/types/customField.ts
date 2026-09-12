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
