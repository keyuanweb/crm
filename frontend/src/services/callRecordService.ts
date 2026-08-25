import { apiClient, type PageResult } from './apiClient'
import type { CallRecord, CallStats } from '../types/callRecord'

export async function fetchCallRecords(params: {
  keyword?: string
  direction?: string
  customerId?: number
  from?: string
  to?: string
  page?: number
  pageSize?: number
}): Promise<PageResult<CallRecord>> {
  const { data } = await apiClient.get('/call-records', { params })
  return data.data as PageResult<CallRecord>
}

export async function createCallRecord(payload: {
  customerId?: number
  contactId?: number
  direction: string
  durationSeconds: number
  result: string
  remark?: string
}): Promise<CallRecord> {
  const { data } = await apiClient.post('/call-records', payload)
  return data.data as CallRecord
}

export async function updateCallRecord(
  id: number,
  payload: {
    customerId?: number
    contactId?: number
    direction: string
    durationSeconds: number
    result: string
    remark?: string
  },
): Promise<CallRecord> {
  const { data } = await apiClient.put(`/call-records/${id}`, payload)
  return data.data as CallRecord
}

export async function deleteCallRecord(id: number): Promise<void> {
  await apiClient.delete(`/call-records/${id}`)
}

export async function fetchCallStats(params: { from?: string; to?: string; direction?: string }): Promise<CallStats> {
  const { data } = await apiClient.get('/call-records/stats', { params })
  return data.data as CallStats
}
