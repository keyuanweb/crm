import { apiClient } from './apiClient'
import type { PipelineStats } from '../types/stats'

export async function fetchPipelineStats(): Promise<PipelineStats> {
  const { data } = await apiClient.get('/stats/opportunity-pipeline')
  return data.data as PipelineStats
}
