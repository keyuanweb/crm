import { apiClient, type PageResult } from './apiClient'
import type { Customer } from '../types/customer'
import type { Segment, SegmentCondition } from '../types/tag'

export async function fetchSegments(): Promise<Segment[]> {
  const { data } = await apiClient.get('/segments')
  return data.data as Segment[]
}

export async function createSegment(payload: {
  name: string
  description?: string
  conditions: string
}): Promise<Segment> {
  const { data } = await apiClient.post('/segments', payload)
  return data.data as Segment
}

export async function updateSegment(
  id: number,
  payload: { name: string; description?: string; conditions: string },
): Promise<Segment> {
  const { data } = await apiClient.put(`/segments/${id}`, payload)
  return data.data as Segment
}

export async function deleteSegment(id: number): Promise<void> {
  await apiClient.delete(`/segments/${id}`)
}

export async function fetchSegmentMembers(
  id: number,
  params: { page?: number; pageSize?: number },
): Promise<PageResult<Customer>> {
  const { data } = await apiClient.get(`/segments/${id}/members`, { params })
  return data.data as PageResult<Customer>
}

export async function fetchSegmentCount(id: number): Promise<number> {
  const { data } = await apiClient.get(`/segments/${id}/count`)
  return data.data as number
}

/** 将 SegmentCondition 序列化为后端 JSON 字符串。 */
export function serializeCondition(cond: SegmentCondition): string {
  return JSON.stringify(cond)
}
