import { apiClient } from './apiClient'

export interface SearchItem {
  id: number
  title: string
  subtitle?: string
  path: string
}

export interface SearchGroup {
  type: string
  label: string
  items: SearchItem[]
}

export interface SearchResponse {
  keyword: string
  groups: SearchGroup[]
  total: number
}

export async function searchAll(keyword: string): Promise<SearchResponse> {
  const { data } = await apiClient.get('/search', { params: { keyword } })
  return data.data as SearchResponse
}

export async function searchFull(keyword: string, type?: string): Promise<SearchResponse> {
  const { data } = await apiClient.get('/search/full', { params: { keyword, type } })
  return data.data as SearchResponse
}
