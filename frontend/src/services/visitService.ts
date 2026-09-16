import { apiClient, type PageResult } from './apiClient'
import i18n from '../i18n'
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

/**
 * 浏览器定位。
 *
 * <p>**服务层取词（098 的第一个先例）**：这里的两条 `new Error(...)` 文案**用户可见** ——
 * 调用方 `pages/VisitListPage.tsx` 是 `message.warning((err as Error).message)`，原样弹给用户。
 * 服务层**拿不到 `useTranslation` 的 hook**（不是组件），故走 i18next **单例** `i18n.t(...)`。
 * 边界：**只用于「非组件模块拿不到 hook」这一种情形**；组件/页面一律仍走 `useTranslation`
 * （`075` FR-P01 的既有裁决）。`i18n.t(` 此前全仓零命中 ⇒ 这是第一条，故在此点名。
 */
export function getCurrentPosition(): Promise<{ latitude: number; longitude: number }> {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error(i18n.t('pages.visit.geoUnsupported')))
      return
    }
    navigator.geolocation.getCurrentPosition(
      (pos) =>
        resolve({
          latitude: pos.coords.latitude,
          longitude: pos.coords.longitude,
        }),
      () => reject(new Error(i18n.t('pages.visit.geoFailed'))),
      { timeout: 8000 },
    )
  })
}
