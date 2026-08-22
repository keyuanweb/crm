export type UserRole = 'ADMIN' | 'SALES' | 'SUPPORT'

export interface User {
  id: number
  username: string
  displayName: string
  role: UserRole
  enabled: boolean
  lastLoginAt?: string
  version: number
  createdAt?: string
}

export interface UserCreatePayload {
  username: string
  displayName: string
  role: UserRole
  password: string
}

export interface UserUpdatePayload {
  displayName?: string
  role?: UserRole
  enabled?: boolean
  version: number
}

export const ROLE_LABELS: Record<UserRole, string> = {
  ADMIN: '管理员',
  SALES: '销售',
  SUPPORT: '客服',
}
