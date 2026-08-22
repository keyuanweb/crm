import { apiClient, type PageResult } from './apiClient'
import type { Lead, LeadDetail, LeadPayload, LeadListParams, ConvertPayload } from '../types/lead'

export type { LeadPayload, ConvertPayload, LeadListParams }

export async function fetchLeads(params: LeadListParams): Promise<PageResult<Lead>> {
  const { data } = await apiClient.get('/leads', { params })
  return data.data as PageResult<Lead>
}

export async function fetchLead(id: number): Promise<LeadDetail> {
  const { data } = await apiClient.get(`/leads/${id}`)
  return data.data as LeadDetail
}

export async function createLead(payload: LeadPayload): Promise<Lead> {
  const { data } = await apiClient.post('/leads', payload)
  return data.data as Lead
}

export async function updateLead(id: number, payload: LeadPayload): Promise<Lead> {
  const { data } = await apiClient.put(`/leads/${id}`, payload)
  return data.data as Lead
}

export async function deleteLead(id: number): Promise<void> {
  await apiClient.delete(`/leads/${id}`)
}

export async function assignLead(id: number, ownerId: number): Promise<Lead> {
  const { data } = await apiClient.post(`/leads/${id}/assign`, null, { params: { ownerId } })
  return data.data as Lead
}

export async function claimLead(id: number): Promise<Lead> {
  const { data } = await apiClient.post(`/leads/${id}/claim`)
  return data.data as Lead
}

export async function convertLead(id: number, payload: ConvertPayload): Promise<LeadDetail> {
  const { data } = await apiClient.post(`/leads/${id}/convert`, payload)
  return data.data as LeadDetail
}
