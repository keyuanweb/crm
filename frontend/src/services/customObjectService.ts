import { apiClient, type PageResult } from './apiClient'
import type { CustomObject, CustomObjectPayload, ObjectRecord } from '../types/customObject'

export async function fetchCustomObjects(
  keyword?: string,
  page = 1,
  pageSize = 20,
): Promise<PageResult<CustomObject>> {
  const { data } = await apiClient.get('/custom-objects', { params: { keyword, page, pageSize } })
  return data.data as PageResult<CustomObject>
}

export async function createCustomObject(payload: CustomObjectPayload): Promise<CustomObject> {
  const { data } = await apiClient.post('/custom-objects', payload)
  return data.data as CustomObject
}

export async function updateCustomObject(id: number, payload: CustomObjectPayload): Promise<CustomObject> {
  const { data } = await apiClient.put(`/custom-objects/${id}`, payload)
  return data.data as CustomObject
}

export async function toggleCustomObject(id: number): Promise<CustomObject> {
  const { data } = await apiClient.post(`/custom-objects/${id}/toggle`)
  return data.data as CustomObject
}

export async function deleteCustomObject(id: number): Promise<void> {
  await apiClient.delete(`/custom-objects/${id}`)
}

export async function fetchObjectRecords(
  objectId: number,
  keyword?: string,
  page = 1,
  pageSize = 20,
): Promise<PageResult<ObjectRecord>> {
  const { data } = await apiClient.get(`/custom-objects/${objectId}/records`, {
    params: { keyword, page, pageSize },
  })
  return data.data as PageResult<ObjectRecord>
}

export async function createObjectRecord(objectId: number, values: Record<string, string>): Promise<ObjectRecord> {
  const { data } = await apiClient.post(`/custom-objects/${objectId}/records`, { values })
  return data.data as ObjectRecord
}

export async function updateObjectRecord(
  objectId: number,
  recordId: number,
  values: Record<string, string>,
): Promise<ObjectRecord> {
  const { data } = await apiClient.put(`/custom-objects/${objectId}/records/${recordId}`, { values })
  return data.data as ObjectRecord
}

export async function deleteObjectRecord(objectId: number, recordId: number): Promise<void> {
  await apiClient.delete(`/custom-objects/${objectId}/records/${recordId}`)
}
