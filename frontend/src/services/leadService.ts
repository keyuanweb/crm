import { apiClient, type PageResult } from './apiClient'
import type { Lead, LeadDetail, LeadPayload, LeadListParams, ConvertPayload } from '../types/lead'
import type { ImportResult } from '../types/importResult'

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

/** 批量导入线索（024）。 */
export async function importLeads(file: File): Promise<ImportResult> {
  const form = new FormData()
  form.append('file', file)
  const { data } = await apiClient.post('/leads/import', form)
  return data.data as ImportResult
}

/** 下载线索导入模板（024）。 */
export async function downloadLeadTemplate(): Promise<void> {
  const resp = await apiClient.get('/leads/template', { responseType: 'blob' })
  const url = URL.createObjectURL(resp.data as Blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'lead-import-template.xlsx'
  a.click()
  URL.revokeObjectURL(url)
}
