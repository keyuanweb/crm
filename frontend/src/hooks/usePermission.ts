import type { UserInfo } from '../store/authStore'

/**
 * 操作权限判断（028-role-permissions，FR-005）：
 * ADMIN 恒有全部权限；其余角色按 user.permissions 权限码判断。
 */
export function hasPerm(code: string, user?: UserInfo | null): boolean {
  if (!user) return false
  if (user.role === 'ADMIN') return true
  return (user.permissions ?? []).includes(code)
}
