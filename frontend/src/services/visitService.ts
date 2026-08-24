import { apiClient, type PageResult } from './apiClient'
import type { CheckInPayload, FieldVisit, VisitPayload, VisitStats } from '../types/visit'

export async function fetchVisits(params: {
  status?: string
  month?: string
  page?: number
  pageSize?: number
}): Promise<PageResult<FieldVisit>> {
  const { data } = await apiClient.get('/field-visits', { params })
  return data.data as PageResult<FieldVisit>
}

export async function createVisit(payload: VisitPayload): Promise<FieldVisit> {
  const { data } = await apiClient.post('/field-visits', payload)
  return data.data as FieldVisit
}

export async function updateVisit(id: number, payload: Partial<VisitPayload>): Promise<FieldVisit> {
  const { data } = await apiClient.put(`/field-visits/${id}`, payload)
  return data.data as FieldVisit
}

export async function cancelVisit(id: number): Promise<void> {
  await apiClient.post(`/field-visits/${id}/cancel`)
}

export async function checkInVisit(id: number, payload: CheckInPayload): Promise<FieldVisit> {
  const { data } = await apiClient.post(`/field-visits/${id}/check-in`, payload)
  return data.data as FieldVisit
}

export async function fetchVisitStats(month?: string): Promise<VisitStats> {
  const { data } = await apiClient.get('/field-visits/stats', { params: { month } })
  return data.data as VisitStats
}

/** 浏览器定位。 */
export function getCurrentPosition(): Promise<{ latitude: number; longitude: number }> {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error('当前浏览器不支持定位'))
      return
    }
    navigator.geolocation.getCurrentPosition(
      (pos) =>
        resolve({
          latitude: pos.coords.latitude,
          longitude: pos.coords.longitude,
        }),
      () => reject(new Error('定位失败，请手动填写坐标或允许定位权限')),
      { timeout: 8000 },
    )
  })
}
