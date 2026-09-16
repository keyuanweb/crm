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

/**
 * 管理员重置某账号的双因素认证（082，FR-M10）。
 *
 * <p>语义是**拆除**：清除该账号的 2FA 密钥与全部恢复码，使其回到单因素登录。用在「员工换了手机、
 * 旧设备也拿不到」这类只有管理员能解的死局上——因为自救通道（恢复码）也被锁在同一个账号里。
 *
 * <p>这是**不可撤销**的：清掉的密钥列不会保留副本，重置之后该账号必须重新走一遍绑定向导。
 * 调用方必须做成一次明确的确认（且确认文案要说清"该账号的二次验证会被关闭"），
 * 不能挂在列表行的一个单点即生效的图标上。
 *
 * <p>与 `mfaService.disableMfa` 的区别在**谁有资格**：那个要本人密码 + 第二因素，
 * 这个要 `user:manage` 权限；两者都不需要对方的凭据，也不互相替代。
 */
export async function resetUserMfa(id: number): Promise<void> {
  await apiClient.post(`/users/${id}/2fa/reset`)
}

export type { UserRole }
