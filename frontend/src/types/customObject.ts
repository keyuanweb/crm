export interface ObjectFieldDef {
  field: string
  label: string
  type: 'TEXT' | 'NUMBER' | 'DATE' | 'SELECT'
  required?: boolean
  options?: string
}

export interface CustomObject {
  id: number
  name: string
  code: string
  fields: ObjectFieldDef[]
  enabled: boolean
  version: number
  createdAt?: string
}

export interface CustomObjectPayload {
  name: string
  code: string
  fields: ObjectFieldDef[]
  enabled?: boolean
  version?: number
}

export interface ObjectRecord {
  id: number
  objectId: number
  values: Record<string, string>
  createdBy?: number
  createdAt?: string
}
