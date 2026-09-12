/**
 * 角色码。
 *
 * <p>改造前是 `'ADMIN' | 'SALES' | 'SUPPORT'` 三个内建角色的联合——081 加入了 10 个预置角色
 * （SALES_MANAGER / SALES_REP / SUPPORT_MANAGER / SUPPORT_AGENT / MARKETING_* / FINANCE_* / ANALYST…），
 * 这个联合就与后端事实脱节了：类型上写 `user.role === 'SALES_MANAGER'` 会被 TS 判为「不可能相等」，
 * 而它运行时是成立的。
 *
 * <p>这里取 `string` 而不是补全成 13 个角色的联合，因为**后端返回的角色是自由字符串**：
 * `RoleService` 允许管理员自建角色（UserManagementPage 的角色下拉就是从 `fetchRoleOptions()`
 * 拉的，不是枚举），联合类型永远补不全，补了反而会诱导别人写死的角色名单。
 * 需要「按角色族分类」的地方（如角色标签配色）用前缀匹配，见 UserManagementPage.roleTagColor。
 */
export type UserRole = string

export interface User {
  id: number
  username: string
  displayName: string
  role: UserRole
  departmentId?: number
  departmentName?: string
  dataScope?: string
  enabled: boolean
  lastLoginAt?: string
  version: number
  createdAt?: string
}

export interface UserCreatePayload {
  username: string
  displayName: string
  role: UserRole
  password: string
}

export interface UserUpdatePayload {
  displayName?: string
  role?: UserRole
  enabled?: boolean
  version: number
}
