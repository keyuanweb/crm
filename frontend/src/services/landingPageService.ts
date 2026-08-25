import { apiClient, type PageResult } from './apiClient'
import type { LandingPage, LandingPagePayload, LandingPagePublic, UtmStats } from '../types/landingPage'

export type { LandingPagePayload }

export async function fetchLandingPages(
  keyword?: string,
  page = 1,
  pageSize = 20,
): Promise<PageResult<LandingPage>> {
  const { data } = await apiClient.get('/landing-pages', { params: { keyword, page, pageSize } })
  return data.data as PageResult<LandingPage>
}

export async function createLandingPage(payload: LandingPagePayload): Promise<LandingPage> {
  const { data } = await apiClient.post('/landing-pages', payload)
  return data.data as LandingPage
}

export async function updateLandingPage(id: number, payload: LandingPagePayload): Promise<LandingPage> {
  const { data } = await apiClient.put(`/landing-pages/${id}`, payload)
  return data.data as LandingPage
}

export async function deleteLandingPage(id: number): Promise<void> {
  await apiClient.delete(`/landing-pages/${id}`)
}

export async function fetchLandingPageStats(
  id: number,
  from?: string,
  to?: string,
): Promise<UtmStats> {
  const { data } = await apiClient.get(`/landing-pages/${id}/stats`, { params: { from, to } })
  return data.data as UtmStats
}

export async function fetchLandingPagePublic(id: number): Promise<LandingPagePublic> {
  const { data } = await apiClient.get(`/public/lp/${id}`)
  return data.data as LandingPagePublic
}
