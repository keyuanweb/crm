import { apiClient, type PageResult } from './apiClient'
import type { Notification } from '../types/notification'

export async function fetchNotifications(
  type?: string,
  page = 1,
  pageSize = 20,
): Promise<PageResult<Notification>> {
  const { data } = await apiClient.get('/notifications', { params: { type, page, pageSize } })
  return data.data as PageResult<Notification>
}

export async function fetchUnreadCount(): Promise<number> {
  const { data } = await apiClient.get('/notifications/unread-count')
  return (data.data as { unreadCount: number }).unreadCount
}

export async function markNotificationRead(id: number): Promise<void> {
  await apiClient.post(`/notifications/${id}/read`)
}

export async function markAllNotificationsRead(): Promise<number> {
  const { data } = await apiClient.post('/notifications/read-all')
  return (data.data as { updated: number }).updated
}
