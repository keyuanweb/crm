import { apiClient, type PageResult } from './apiClient'
import type {
  Ticket,
  TicketListParams,
  TicketPayload,
  TicketReply,
} from '../types/ticket'

export type { TicketListParams, TicketPayload }

export async function fetchTickets(params: TicketListParams): Promise<PageResult<Ticket>> {
  const { data } = await apiClient.get('/tickets', { params })
  return data.data as PageResult<Ticket>
}

export async function fetchTicket(id: number): Promise<Ticket> {
  const { data } = await apiClient.get(`/tickets/${id}`)
  return data.data as Ticket
}

export async function fetchTicketReplies(
  id: number,
  page: number,
  pageSize: number,
): Promise<PageResult<TicketReply>> {
  const { data } = await apiClient.get(`/tickets/${id}/replies`, { params: { page, pageSize } })
  return data.data as PageResult<TicketReply>
}

export async function createTicket(payload: TicketPayload): Promise<Ticket> {
  const { data } = await apiClient.post('/tickets', payload)
  return data.data as Ticket
}

export async function updateTicket(id: number, payload: TicketPayload): Promise<Ticket> {
  const { data } = await apiClient.put(`/tickets/${id}`, payload)
  return data.data as Ticket
}

export async function assignTicket(id: number, assigneeId: number): Promise<Ticket> {
  const { data } = await apiClient.post(`/tickets/${id}/assign`, { assigneeId })
  return data.data as Ticket
}

export async function replyTicket(id: number, content: string): Promise<TicketReply> {
  const { data } = await apiClient.post(`/tickets/${id}/reply`, { content })
  return data.data as TicketReply
}

export async function transitionTicket(id: number, targetStatus: string): Promise<Ticket> {
  const { data } = await apiClient.post(`/tickets/${id}/transition`, { targetStatus })
  return data.data as Ticket
}

export async function deleteTicket(id: number): Promise<void> {
  await apiClient.delete(`/tickets/${id}`)
}
