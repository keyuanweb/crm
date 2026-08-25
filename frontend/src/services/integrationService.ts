import { apiClient, type PageResult } from './apiClient'
import type { IntegrationChannel } from '../types/integration'

export async function fetchChannels(): Promise<IntegrationChannel[]> {
  const { data } = await apiClient.get('/integration-channels')
  return data.data as IntegrationChannel[]
}

export async function createChannel(payload: {
  channelType: string
  name: string
  webhookUrl: string
  enabled?: boolean
}): Promise<IntegrationChannel> {
  const { data } = await apiClient.post('/integration-channels', payload)
  return data.data as IntegrationChannel
}

export async function updateChannel(id: number, payload: {
  channelType: string
  name: string
  webhookUrl: string
  enabled?: boolean
}): Promise<IntegrationChannel> {
  const { data } = await apiClient.put(`/integration-channels/${id}`, payload)
  return data.data as IntegrationChannel
}

export async function toggleChannel(id: number): Promise<IntegrationChannel> {
  const { data } = await apiClient.post(`/integration-channels/${id}/toggle`)
  return data.data as IntegrationChannel
}

export async function deleteChannel(id: number): Promise<void> {
  await apiClient.delete(`/integration-channels/${id}`)
}

export async function fetchChannelDeliveries(
  id: number,
  page = 1,
  pageSize = 20,
): Promise<PageResult<{ id: number; eventType: string; status: string; httpStatus?: number; error?: string; createdAt: string }>> {
  const { data } = await apiClient.get(`/integration-channels/${id}/deliveries`, {
    params: { page, pageSize },
  })
  return data.data as PageResult<{
    id: number
    eventType: string
    status: string
    httpStatus?: number
    error?: string
    createdAt: string
  }>
}
