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

export const DATA_SCOPE_LABELS: Record<DataScope, string> = {
  SELF: '本人',
  DEPT: '本部门',
  DEPT_AND_CHILD: '本部门及下级',
  ALL: '全部',
}
