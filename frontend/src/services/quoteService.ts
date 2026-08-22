import { apiClient, type PageResult } from './apiClient'
import type { Quote, QuoteListParams, QuotePayload } from '../types/quote'

export type { QuoteListParams, QuotePayload }

export async function fetchQuotes(params: QuoteListParams): Promise<PageResult<Quote>> {
  const { data } = await apiClient.get('/quotes', { params })
  return data.data as PageResult<Quote>
}

export async function fetchQuote(id: number): Promise<Quote> {
  const { data } = await apiClient.get(`/quotes/${id}`)
  return data.data as Quote
}

export async function createQuote(payload: QuotePayload): Promise<Quote> {
  const { data } = await apiClient.post('/quotes', payload)
  return data.data as Quote
}

export async function updateQuote(id: number, payload: QuotePayload): Promise<Quote> {
  const { data } = await apiClient.put(`/quotes/${id}`, payload)
  return data.data as Quote
}

export async function submitQuote(id: number): Promise<Quote> {
  const { data } = await apiClient.post(`/quotes/${id}/submit`)
  return data.data as Quote
}

export async function approveQuote(id: number): Promise<Quote> {
  const { data } = await apiClient.post(`/quotes/${id}/approve`)
  return data.data as Quote
}

export async function rejectQuote(id: number, reason: string): Promise<Quote> {
  const { data } = await apiClient.post(`/quotes/${id}/reject`, { reason })
  return data.data as Quote
}

/** 导出报价单 PDF（blob 下载）。 */
export async function exportQuotePdf(id: number): Promise<void> {
  const resp = await apiClient.get(`/quotes/${id}/pdf`, { responseType: 'blob' })
  const url = URL.createObjectURL(resp.data as Blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `quote-${id}.pdf`
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}
