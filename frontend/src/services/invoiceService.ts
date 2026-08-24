import { apiClient, type PageResult } from './apiClient'
import type { Invoice, InvoicePayload, InvoiceStats } from '../types/invoice'

export async function fetchInvoices(params: {
  orderId?: number
  status?: string
  invoiceType?: string
  page?: number
  pageSize?: number
}): Promise<PageResult<Invoice>> {
  const { data } = await apiClient.get('/invoices', { params })
  return data.data as PageResult<Invoice>
}

export async function createInvoice(payload: InvoicePayload): Promise<Invoice> {
  const { data } = await apiClient.post('/invoices', payload)
  return data.data as Invoice
}

export async function voidInvoice(id: number, reason: string): Promise<Invoice> {
  const { data } = await apiClient.post(`/invoices/${id}/void`, { reason })
  return data.data as Invoice
}

export async function fetchInvoiceStats(): Promise<InvoiceStats> {
  const { data } = await apiClient.get('/invoices/stats')
  return data.data as InvoiceStats
}

export async function fetchOrdersForInvoice(): Promise<{ id: number; orderNo: string; amount: number }[]> {
  const { data } = await apiClient.get('/orders', { params: { page: 1, pageSize: 100 } })
  return data.data.items as { id: number; orderNo: string; amount: number }[]
}
