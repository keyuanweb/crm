import { apiClient } from './apiClient'
import type { UserInfo } from '../store/authStore'

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  user: UserInfo
}

export interface CaptchaResponse {
  captchaId: string
  imageBase64: string
}

export async function fetchCaptcha(): Promise<CaptchaResponse> {
  const { data } = await apiClient.get('/auth/captcha')
  return data.data as CaptchaResponse
}

export async function login(
  username: string,
  password: string,
  captchaId?: string,
  captchaCode?: string,
): Promise<AuthResponse> {
  const { data } = await apiClient.post('/auth/login', {
    username,
    password,
    captchaId,
    captchaCode,
  })
  return data.data as AuthResponse
}

export async function fetchMe(): Promise<UserInfo> {
  const { data } = await apiClient.get('/auth/me')
  return data.data as UserInfo
}

export async function logout(refreshToken: string): Promise<void> {
  await apiClient.post('/auth/logout', { refreshToken })
}
