import { apiClient } from './apiClient'
import type { UserInfo } from '../store/authStore'

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  user: UserInfo
}

export async function login(username: string, password: string): Promise<AuthResponse> {
  const { data } = await apiClient.post('/auth/login', { username, password })
  return data.data as AuthResponse
}

export async function fetchMe(): Promise<UserInfo> {
  const { data } = await apiClient.get('/auth/me')
  return data.data as UserInfo
}

export async function logout(refreshToken: string): Promise<void> {
  await apiClient.post('/auth/logout', { refreshToken })
}
