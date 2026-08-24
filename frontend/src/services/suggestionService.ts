import { apiClient } from './apiClient'
import type { SmartSuggestion, SuggestionSummary } from '../types/suggestion'

export async function fetchSuggestions(limit = 20): Promise<SmartSuggestion[]> {
  const { data } = await apiClient.get('/suggestions', { params: { limit } })
  return data.data.items as SmartSuggestion[]
}

export async function ignoreSuggestion(type: string, entityId: number): Promise<void> {
  await apiClient.post(`/suggestions/${type}/${entityId}/ignore`)
}

export async function fetchSuggestionSummary(): Promise<SuggestionSummary> {
  const { data } = await apiClient.get('/suggestions/summary')
  return data.data as SuggestionSummary
}
