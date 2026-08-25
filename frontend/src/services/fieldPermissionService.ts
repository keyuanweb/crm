import { apiClient, type PageResult } from './apiClient'
import type { FieldPermission } from '../types/fieldPermission'

export async function fetchFieldPermissions(
  roleCode?: string,
  entityType?: string,
  page = 1,
  pageSize = 50,
): Promise<PageResult<FieldPermission>> {
  const { data } = await apiClient.get('/field-permissions', {
    params: { roleCode, entityType, page, pageSize },
  })
  return data.data as PageResult<FieldPermission>
}

export async function upsertFieldPermission(payload: {
  roleCode: string
  entityType: string
  fieldId: number
  permission: string
}): Promise<FieldPermission> {
  const { data } = await apiClient.post('/field-permissions', payload)
  return data.data as FieldPermission
}

export async function deleteFieldPermission(id: number): Promise<void> {
  await apiClient.delete(`/field-permissions/${id}`)
}
