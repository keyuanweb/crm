import { apiClient } from './apiClient'
import type { SurveyStats, TicketSurvey } from '../types/survey'

export async function submitTicketSurvey(
  ticketId: number,
  rating: number,
  comment?: string,
): Promise<TicketSurvey> {
  const { data } = await apiClient.post(`/tickets/${ticketId}/survey`, { rating, comment })
  return data.data as TicketSurvey
}

export async function fetchTicketSurvey(ticketId: number): Promise<TicketSurvey | null> {
  const { data } = await apiClient.get(`/tickets/${ticketId}/survey`)
  return data.data as TicketSurvey | null
}

export async function fetchSurveyStats(from?: string, to?: string): Promise<SurveyStats> {
  const { data } = await apiClient.get('/surveys/stats', { params: { from, to } })
  return data.data as SurveyStats
}
