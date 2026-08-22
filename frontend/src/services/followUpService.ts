import { apiClient, type PageResult } from './apiClient'
import type { FollowUp, FollowUpMethod } from '../types/followUp'

export interface FollowUpPayload {
  customerId?: number
  leadId?: number
  opportunityId?: number
  method: FollowUpMethod
  content: string
  nextFollowUpAt?: string
  version?: number
}

export async function fetchFollowUps(params: {
  customerId?: number
  leadId?: number
  opportunityId?: number
  page: number
  pageSize: number
}): Promise<PageResult<FollowUp>> {
  const { data } = await apiClient.get('/follow-ups', { params })
  return data.data as PageResult<FollowUp>
}

export async function createFollowUp(payload: FollowUpPayload): Promise<FollowUp> {
  const { data } = await apiClient.post('/follow-ups', payload)
  return data.data as FollowUp
}

export async function updateFollowUp(id: number, payload: FollowUpPayload): Promise<FollowUp> {
  const { data } = await apiClient.put(`/follow-ups/${id}`, payload)
  return data.data as FollowUp
}
