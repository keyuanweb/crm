import { create } from 'zustand'

export type Role = 'ADMIN' | 'SALES' | 'SUPPORT'

export interface UserInfo {
  id: number
  username: string
  displayName: string
  role: Role
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
