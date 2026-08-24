import { apiClient, type PageResult } from './apiClient'
import type { RecycleItem, RecycleType } from '../types/recycle'

export async function fetchRecycleBin(params: {
  type?: string
  keyword?: string
  page?: number
  pageSize?: number
}): Promise<PageResult<RecycleItem>> {
  const { data } = await apiClient.get('/recycle-bin', { params })
  return data.data as PageResult<RecycleItem>
}

export async function restoreItems(items: { type: RecycleType; id: number }[]): Promise<{ restoredCount: number; failures: { type: string; id: string; message: string }[] }> {
  const { data } = await apiClient.post('/recycle-bin/restore', { items })
  return data.data as { restoredCount: number; failures: { type: string; id: string; message: string }[] }
}

export async function purgeItems(items: { type: RecycleType; id: number }[]): Promise<{ purgedCount: number }> {
  const { data } = await apiClient.post('/recycle-bin/purge', { items })
  return data.data as { purgedCount: number }
}
