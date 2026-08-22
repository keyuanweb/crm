import { apiClient, type PageResult } from './apiClient'
import type { User, UserCreatePayload, UserRole, UserUpdatePayload } from '../types/user'

export async function fetchUsers(params: {
  keyword?: string
  role?: string
  page: number
  pageSize: number
}): Promise<PageResult<User>> {
  const { data } = await apiClient.get('/users', { params })
  return data.data as PageResult<User>
}

export async function createUser(payload: UserCreatePayload): Promise<User> {
  const { data } = await apiClient.post('/users', payload)
  return data.data as User
}

export async function updateUser(id: number, payload: UserUpdatePayload): Promise<User> {
  const { data } = await apiClient.put(`/users/${id}`, payload)
  return data.data as User
}

export async function resetPassword(id: number, newPassword: string): Promise<void> {
  await apiClient.put(`/users/${id}/password`, { newPassword })
}

export async function changeOwnPassword(oldPassword: string, newPassword: string): Promise<void> {
  await apiClient.put('/users/me/password', { oldPassword, newPassword })
}

export type { UserRole }
