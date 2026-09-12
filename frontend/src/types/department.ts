export interface Department {
  id: number
  name: string
  parentId?: number
  description?: string
  sortOrder?: number
  version: number
  createdAt?: string
  createdBy?: string
  memberCount?: number
  childCount?: number
  children: Department[]
}

export interface DepartmentPayload {
  name: string
  parentId?: number
  description?: string
  sortOrder?: number
  version?: number
}

export type DataScope = 'SELF' | 'DEPT' | 'DEPT_AND_CHILD' | 'ALL'
