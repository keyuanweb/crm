import { useAuthStore } from '../store/authStore'
import type { UserInfo } from '../store/authStore'

/**
 * 操作权限判断（028-role-permissions，FR-005；081 扩展）：
 * ADMIN 恒有全部权限；其余角色按 user.permissions 权限码判断。
 */
export function hasPerm(code: string, user?: UserInfo | null): boolean {
  if (!user) return false
  if (user.role === 'ADMIN') return true
  return (user.permissions ?? []).includes(code)
}

/** 检查用户是否有指定权限。 */
export function useHasPermission() {
  const user = useAuthStore((state) => state.user)
  return (permission: string): boolean => hasPerm(permission, user)
}

/** 检查用户是否有指定角色。 */
export function useHasRole() {
  const user = useAuthStore((state) => state.user)
  return (role: string): boolean => {
    if (!user) return false
    return user.role === role
  }
}

/** 检查用户是否有指定菜单权限。 */
export function useHasMenu() {
  const user = useAuthStore((state) => state.user)
  return (menuKey: string): boolean => {
    if (!user) return false
    if (user.role === 'ADMIN') return true
    const menus = user.menus || []
    return menus.includes(menuKey)
  }
}
