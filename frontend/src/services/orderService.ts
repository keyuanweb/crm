import { apiClient, type PageResult } from './apiClient'
import type { Order, OrderListParams, OrderPayload, PaymentPayload } from '../types/order'

export type { OrderListParams, OrderPayload, PaymentPayload }

export async function fetchOrders(params: OrderListParams): Promise<PageResult<Order>> {
  const { data } = await apiClient.get('/orders', { params })
  return data.data as PageResult<Order>
}

export async function fetchOrder(id: number): Promise<Order> {
  const { data } = await apiClient.get(`/orders/${id}`)
  return data.data as Order
}

export async function createOrder(payload: OrderPayload): Promise<Order> {
  const { data } = await apiClient.post('/orders', payload)
  return data.data as Order
}

export async function updateOrder(id: number, payload: OrderPayload): Promise<Order> {
  const { data } = await apiClient.put(`/orders/${id}`, payload)
  return data.data as Order
}

export async function deleteOrder(id: number): Promise<void> {
  await apiClient.delete(`/orders/${id}`)
}

export async function recordPayment(orderId: number, payload: PaymentPayload): Promise<Order> {
  const { data } = await apiClient.post(`/orders/${orderId}/payments`, payload)
  return data.data as Order
}

export async function fetchReminderSummary(): Promise<{ overdueCount: number; dueSoonCount: number }> {
  const { data } = await apiClient.get('/orders/reminder-summary')
  return data.data as { overdueCount: number; dueSoonCount: number }
}
