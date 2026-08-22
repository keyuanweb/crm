import { apiClient, type PageResult } from './apiClient'
import type { CustomField, CustomFieldPayload } from '../types/customField'

export type { CustomFieldPayload }

export async function fetchCustomFields(
  entityType?: string,
  page = 1,
  pageSize = 50,
): Promise<PageResult<CustomField>> {
  const { data } = await apiClient.get('/custom-fields', { params: { entityType, page, pageSize } })
  return data.data as PageResult<CustomField>
}

export async function fetchFieldDefinitions(entityType: string): Promise<CustomField[]> {
  const { data } = await apiClient.get('/custom-fields/definitions', { params: { entityType } })
  return data.data as CustomField[]
}

export async function createCustomField(payload: CustomFieldPayload): Promise<CustomField> {
  const { data } = await apiClient.post('/custom-fields', payload)
  return data.data as CustomField
}

export async function updateCustomField(id: number, payload: CustomFieldPayload): Promise<CustomField> {
  const { data } = await apiClient.put(`/custom-fields/${id}`, payload)
  return data.data as CustomField
}

export async function deleteCustomField(id: number): Promise<void> {
  await apiClient.delete(`/custom-fields/${id}`)
}
