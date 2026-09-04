/** 个人信息响应。 */
export interface PersonalInfo {
  id: number
  username: string
  displayName: string
  role: string
  departmentId?: number | null
  departmentName?: string | null
  dataScope?: string | null
  enabled?: boolean | null
  lastLoginAt?: string | null
  createdAt?: string | null
  passwordUpdatedAt?: string | null
}

/** 更新显示名请求。 */
export interface UpdateDisplayNamePayload {
  displayName: string
}

/** 修改密码请求。 */
export interface ChangePasswordPayload {
  oldPassword: string
  newPassword: string
}
