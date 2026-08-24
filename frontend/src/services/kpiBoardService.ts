import { apiClient } from './apiClient'
import type { KpiBoard } from '../types/kpiBoard'

export async function fetchKpiBoard(): Promise<KpiBoard> {
  const { data } = await apiClient.get('/stats/kpi-board')
  return data.data as KpiBoard
}
