import { apiClient } from './apiClient'
import type {
  DashboardStats,
  PipelineStats,
  SalesTarget,
  SalesTargetPayload,
} from '../types/stats'

export async function fetchPipelineStats(): Promise<PipelineStats> {
  const { data } = await apiClient.get('/stats/opportunity-pipeline')
  return data.data as PipelineStats
}

export async function fetchDashboardStats(): Promise<DashboardStats> {
  const { data } = await apiClient.get('/stats/dashboard')
  return data.data as DashboardStats
}

export async function fetchSalesTarget(month: string): Promise<SalesTarget> {
  const { data } = await apiClient.get('/stats/sales-targets', { params: { month } })
  return data.data as SalesTarget
}

export async function saveSalesTarget(payload: SalesTargetPayload): Promise<SalesTarget> {
  const { data } = await apiClient.put('/stats/sales-targets', payload)
  return data.data as SalesTarget
}
