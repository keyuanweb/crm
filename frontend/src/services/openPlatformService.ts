import { apiClient, type PageResult } from './apiClient'
import type { ApiKey, WebhookDelivery, WebhookSubscription } from '../types/openPlatform'

export async function createApiKey(payload: {
  name: string
  scopes?: string[]
  expiresAt?: string
}): Promise<ApiKey> {
  const { data } = await apiClient.post('/platform/api-keys', payload)
  return data.data as ApiKey
}

export async function fetchApiKeys(page = 1, pageSize = 20): Promise<PageResult<ApiKey>> {
  const { data } = await apiClient.get('/platform/api-keys', { params: { page, pageSize } })
  return data.data as PageResult<ApiKey>
}

export async function revokeApiKey(id: number): Promise<void> {
  await apiClient.post(`/platform/api-keys/${id}/revoke`)
}

export async function createWebhook(payload: {
  eventType: string
  callbackUrl: string
}): Promise<WebhookSubscription> {
  const { data } = await apiClient.post('/platform/webhooks', payload)
  return data.data as WebhookSubscription
}

export async function fetchWebhooks(): Promise<WebhookSubscription[]> {
  const { data } = await apiClient.get('/platform/webhooks')
  return data.data as WebhookSubscription[]
}

export async function toggleWebhook(id: number): Promise<WebhookSubscription> {
  const { data } = await apiClient.post(`/platform/webhooks/${id}/toggle`)
  return data.data as WebhookSubscription
}

export async function deleteWebhook(id: number): Promise<void> {
  await apiClient.delete(`/platform/webhooks/${id}`)
}

export async function fetchWebhookDeliveries(
  id: number,
  page = 1,
  pageSize = 20,
): Promise<PageResult<WebhookDelivery>> {
  const { data } = await apiClient.get(`/platform/webhooks/${id}/deliveries`, {
    params: { page, pageSize },
  })
  return data.data as PageResult<WebhookDelivery>
}
