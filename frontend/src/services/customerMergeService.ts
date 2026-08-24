import { apiClient } from './apiClient'
import type { DuplicateGroup, MergeResult } from '../types/merge'

export async function fetchDuplicates(): Promise<DuplicateGroup[]> {
  const { data } = await apiClient.get('/customers/duplicates')
  return data.data as DuplicateGroup[]
}

export async function mergeCustomers(primaryId: number, duplicateId: number): Promise<MergeResult> {
  const { data } = await apiClient.post('/customers/merge', { primaryId, duplicateId })
  return data.data as MergeResult
}
