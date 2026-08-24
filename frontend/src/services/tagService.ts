import { apiClient } from './apiClient'
import type { Tag, TagPayload } from '../types/tag'

export async function fetchTags(entityType = 'CUSTOMER'): Promise<Tag[]> {
  const { data } = await apiClient.get('/tags', { params: { entityType } })
  return data.data as Tag[]
}

export async function createTag(payload: TagPayload): Promise<Tag> {
  const { data } = await apiClient.post('/tags', payload)
  return data.data as Tag
}

export async function updateTag(id: number, payload: Partial<TagPayload>): Promise<Tag> {
  const { data } = await apiClient.put(`/tags/${id}`, payload)
  return data.data as Tag
}

export async function deleteTag(id: number): Promise<void> {
  await apiClient.delete(`/tags/${id}`)
}

export async function setCustomerTags(customerId: number, tagIds: number[]): Promise<void> {
  await apiClient.put(`/tags/customers/${customerId}/tags`, { tagIds })
}

export async function fetchCustomerTags(customerId: number): Promise<Tag[]> {
  const { data } = await apiClient.get(`/tags/customers/${customerId}/tags`)
  return data.data as Tag[]
}
