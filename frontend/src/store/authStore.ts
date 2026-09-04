import { create } from 'zustand'

export type Role = 'ADMIN' | 'SALES' | 'SUPPORT' | string

export interface UserInfo {
  id: number
  username: string
  displayName: string
  role: Role
  /** 所属部门 ID。 */
  departmentId?: number
  /** 所属部门名称。 */
  departmentName?: string
  /** 数据权限范围。 */
  dataScope?: string
  /** 启用状态。 */
  enabled?: boolean
  /** 最后登录时间。 */
  lastLoginAt?: string
  /** 创建时间。 */
  createdAt?: string
  /** 可见菜单 key（028 角色权限）。 */
  menus?: string[]
  /** 操作权限码（028 角色权限）。 */
  permissions?: string[]
}

interface AuthState {
  user: UserInfo | null
  setUser: (user: UserInfo | null) => void
  setTokens: (accessToken: string, refreshToken: string) => void
  clear: () => void
  isAuthenticated: () => boolean
  getAccessToken: () => string | null
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  setUser: (user) => set({ user }),
  setTokens: (accessToken, refreshToken) => {
    localStorage.setItem('accessToken', accessToken)
    localStorage.setItem('refreshToken', refreshToken)
  },
  clear: () => {
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    set({ user: null })
  },
  isAuthenticated: () => Boolean(localStorage.getItem('accessToken')),
  getAccessToken: () => localStorage.getItem('accessToken'),
}))
