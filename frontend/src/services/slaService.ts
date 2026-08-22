import { apiClient, type PageResult } from './apiClient'
import type { SlaOverview, SlaPolicy, SlaPolicyPayload } from '../types/sla'

export type { SlaPolicyPayload }

export async function fetchSlaPolicies(page: number, pageSize: number): Promise<PageResult<SlaPolicy>> {
  const { data } = await apiClient.get('/sla-policies', { params: { page, pageSize } })
  return data.data as PageResult<SlaPolicy>
}

export async function createSlaPolicy(payload: SlaPolicyPayload): Promise<SlaPolicy> {
  const { data } = await apiClient.post('/sla-policies', payload)
  return data.data as SlaPolicy
}

export async function updateSlaPolicy(id: number, payload: SlaPolicyPayload): Promise<SlaPolicy> {
  const { data } = await apiClient.put(`/sla-policies/${id}`, payload)
  return data.data as SlaPolicy
}

export async function deleteSlaPolicy(id: number): Promise<void> {
  await apiClient.delete(`/sla-policies/${id}`)
}

export async function fetchSlaOverview(): Promise<SlaOverview> {
  const { data } = await apiClient.get('/sla-policies/overview')
  return data.data as SlaOverview
}
