import { apiClient } from './apiClient'
import type { ReportQuery, ReportResult } from '../types/report'

export async function queryReport(query: ReportQuery): Promise<ReportResult> {
  const { data } = await apiClient.post('/reports/query', query)
  return data.data as ReportResult
}

/** 导出报表（下载 xlsx）。 */
export async function exportReport(query: ReportQuery): Promise<void> {
  const resp = await apiClient.get('/reports/export', {
    params: query,
    responseType: 'blob',
  })
  const url = URL.createObjectURL(resp.data as Blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'report.xlsx'
  a.click()
  URL.revokeObjectURL(url)
}
