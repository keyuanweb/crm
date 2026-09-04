/** 权限守卫组件（081-role-permissions-update）。

根据用户权限控制子组件的渲染。
 */
import React from 'react'
import { useAuthStore } from '../store/authStore'
import { hasPerm } from '../hooks/usePermission'

interface PermissionGuardProps {
  /** 权限码，格式：实体:动作（如 customer:create） */
  permission?: string
  /** 多个权限码（满足任一即可） */
  permissions?: string[]
  /** 角色编码（如 ADMIN、SALES_MANAGER） */
  role?: string
  /** 多个角色编码（满足任一即可） */
  roles?: string[]
  /** 权限不足时渲染的内容 */
  fallback?: React.ReactNode
  children: React.ReactNode
}

/** 权限守卫组件：根据权限/角色控制子组件渲染。 */
const PermissionGuard: React.FC<PermissionGuardProps> = ({
  permission,
  permissions,
  role,
  roles,
  fallback = null,
  children,
}) => {
  const user = useAuthStore((state) => state.user)
  if (!user) return <>{fallback}</>

  // ADMIN 角色拥有所有权限
  if (user.role === 'ADMIN') return <>{children}</>

  // 检查角色权限
  if (role || roles) {
    const allowedRoles = roles || [role].filter(Boolean) as string[]
    if (!allowedRoles.includes(user.role)) {
      return <>{fallback}</>
    }
  }

  // 检查权限码
  if (permission || permissions) {
    const allowedPermissions = permissions || [permission].filter(Boolean) as string[]
    const hasPermission = allowedPermissions.some((p) => hasPerm(p, user))
    if (!hasPermission) {
      return <>{fallback}</>
    }
  }

  return <>{children}</>
}

export default PermissionGuard
