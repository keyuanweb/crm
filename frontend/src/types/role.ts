/** 角色（028-role-permissions）。 */
export interface Role {
  id: number
  code: string
  name: string
  description?: string
  dataScope: 'ALL' | 'DEPT' | 'SELF'
  enabled: boolean
  builtIn: boolean
  menus: string[]
  permissions: string[]
}

export interface RoleOption {
  id: number
  code: string
  name: string
  dataScope: string
}

export interface RolePayload {
  code: string
  name: string
  description?: string
  dataScope: string
  enabled: boolean
  menus: string[]
  permissions: string[]
}

/** 菜单树节点（配置页勾选）。 */
export interface MenuTreeNode {
  title: string
  children: { key: string; title: string }[]
}

/** 权限点分组（配置页勾选）。 */
export interface PermissionDefGroup {
  title: string
  children: { code: string; label: string }[]
}
