import { apiClient, type PageResult } from './apiClient'
import type { Announcement, AnnouncementPayload } from '../types/announcement'

export async function fetchAnnouncements(params: {
  page?: number
  pageSize?: number
}): Promise<PageResult<Announcement>> {
  const { data } = await apiClient.get('/announcements', { params })
  return data.data as PageResult<Announcement>
}

export async function createAnnouncement(payload: AnnouncementPayload): Promise<Announcement> {
  const { data } = await apiClient.post('/announcements', payload)
  return data.data as Announcement
}

export async function updateAnnouncement(
  id: number,
  payload: Partial<AnnouncementPayload>,
): Promise<Announcement> {
  const { data } = await apiClient.put(`/announcements/${id}`, payload)
  return data.data as Announcement
}

export async function deleteAnnouncement(id: number): Promise<void> {
  await apiClient.delete(`/announcements/${id}`)
}

export async function markAnnouncementRead(id: number): Promise<void> {
  await apiClient.post(`/announcements/${id}/read`)
}

export async function fetchUnreadCount(): Promise<number> {
  const { data } = await apiClient.get('/announcements/unread-count')
  return data.data as number
}
