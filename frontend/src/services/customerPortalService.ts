import { apiClient, type PageResult } from './apiClient'
import type { PortalArticle, PortalTicketResult, PortalTicketStatus } from '../types/portal'

const PORTAL_BASE = '/public/portal'

export async function fetchPortalArticles(
  keyword?: string,
  page = 1,
  pageSize = 20,
): Promise<PageResult<PortalArticle>> {
  const { data } = await apiClient.get(`${PORTAL_BASE}/articles`, {
    params: { keyword, page, pageSize },
  })
  return data.data as PageResult<PortalArticle>
}

export async function fetchPortalArticle(id: number): Promise<PortalArticle> {
  const { data } = await apiClient.get(`${PORTAL_BASE}/articles/${id}`)
  return data.data as PortalArticle
}

export async function submitPortalTicket(payload: {
  phone?: string
  email?: string
  title: string
  description?: string
  priority?: string
}): Promise<PortalTicketResult> {
  const { data } = await apiClient.post(`${PORTAL_BASE}/tickets`, payload)
  return data.data as PortalTicketResult
}

export async function queryPortalTicket(payload: {
  ticketId: number
  phone?: string
  email?: string
}): Promise<PortalTicketStatus> {
  const { data } = await apiClient.post(`${PORTAL_BASE}/tickets/status`, payload)
  return data.data as PortalTicketStatus
}
