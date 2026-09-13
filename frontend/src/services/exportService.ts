import { apiClient, type PageResult } from './apiClient'
import type { ExportJob, ExportPayload } from '../types/export'

export async function createExportJob(payload: ExportPayload): Promise<ExportJob> {
  const { data } = await apiClient.post('/exports', payload)
  return data.data as ExportJob
}

export async function fetchExportJobs(page = 1, pageSize = 20): Promise<PageResult<ExportJob>> {
  const { data } = await apiClient.get('/exports', { params: { page, pageSize } })
  return data.data as PageResult<ExportJob>
}

export async function downloadExportJob(id: number): Promise<void> {
  // 导出件的下载耗时由文件大小决定 ⇒ 退出全局 30s 超时（见 `apiClient.REQUEST_TIMEOUT_MS`）。
  const response = await apiClient.get(`/exports/${id}/download`, { responseType: 'blob', timeout: 0 })
  const url = window.URL.createObjectURL(new Blob([response.data as BlobPart]))
  const link = document.createElement('a')
  link.href = url
  const disposition = response.headers['content-disposition'] as string | undefined
  const match = disposition?.match(/filename="?([^";]+)"?/)
  link.download = match?.[1] ?? `export-${id}.xlsx`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}
