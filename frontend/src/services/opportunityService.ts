import { apiClient, type PageResult } from './apiClient'
import type {
  CloseResult,
  Opportunity,
  OpportunityDetail,
  OpportunityStage,
  SalesOpportunity,
} from '../types/opportunity'

export interface OpportunityPayload {
  customerId: number
  name: string
  expectedAmountMin?: number
  expectedAmountMax?: number
  remark?: string
  status?: string
  version?: number
}

export interface SalesOpportunityPayload {
  opportunityId: number
  amount?: number
  stage: OpportunityStage
  expectedCloseDate?: string
  version?: number
}

export async function fetchOpportunities(params: {
  keyword?: string
  customerId?: number
  status?: string
  page: number
  pageSize: number
}): Promise<PageResult<Opportunity>> {
  const { data } = await apiClient.get('/opportunities', { params })
  return data.data as PageResult<Opportunity>
}

export async function fetchOpportunity(id: number): Promise<OpportunityDetail> {
  const { data } = await apiClient.get(`/opportunities/${id}`)
  return data.data as OpportunityDetail
}

export async function createOpportunity(payload: OpportunityPayload): Promise<Opportunity> {
  const { data } = await apiClient.post('/opportunities', payload)
  return data.data as Opportunity
}

export async function updateOpportunity(
  id: number,
  payload: OpportunityPayload,
): Promise<Opportunity> {
  const { data } = await apiClient.put(`/opportunities/${id}`, payload)
  return data.data as Opportunity
}

export async function deleteOpportunity(id: number): Promise<void> {
  await apiClient.delete(`/opportunities/${id}`)
}

export async function fetchSalesOpportunities(params: {
  stage?: string
  opportunityId?: number
  customerId?: number
  page: number
  pageSize: number
}): Promise<PageResult<SalesOpportunity>> {
  const { data } = await apiClient.get('/sales-opportunities', { params })
  return data.data as PageResult<SalesOpportunity>
}

export async function createSalesOpportunity(
  payload: SalesOpportunityPayload,
): Promise<SalesOpportunity> {
  const { data } = await apiClient.post('/sales-opportunities', payload)
  return data.data as SalesOpportunity
}

export async function updateSalesOpportunity(
  id: number,
  payload: SalesOpportunityPayload,
): Promise<SalesOpportunity> {
  const { data } = await apiClient.put(`/sales-opportunities/${id}`, payload)
  return data.data as SalesOpportunity
}

export async function closeSalesOpportunity(
  id: number,
  closeResult: CloseResult,
  version: number,
): Promise<SalesOpportunity> {
  const { data } = await apiClient.post(`/sales-opportunities/${id}/close`, {
    closeResult,
    version,
  })
  return data.data as SalesOpportunity
}
