export interface Department {
  id: number
  name: string
  parentId?: number
  version: number
  createdAt?: string
  children: Department[]
}

export interface DepartmentPayload {
  name: string
  parentId?: number
  version?: number
}

export type DataScope = 'SELF' | 'DEPT' | 'DEPT_AND_CHILD' | 'ALL'

export const DATA_SCOPE_LABELS: Record<DataScope, string> = {
  SELF: '本人',
  DEPT: '本部门',
  DEPT_AND_CHILD: '本部门及下级',
  ALL: '全部',
}
