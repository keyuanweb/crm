import { apiClient } from './apiClient'
import type { PersonalInfo, UpdateDisplayNamePayload } from '../types/personal'

/** 获取当前用户个人信息。 */
export async function fetchPersonalInfo(): Promise<PersonalInfo> {
  const response = await apiClient.get('/personal/info')
  return response.data.data
}

/** 修改当前用户显示名。 */
export async function updateDisplayName(payload: UpdateDisplayNamePayload): Promise<void> {
  await apiClient.put('/personal/display-name', payload)
}
