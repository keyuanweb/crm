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
    // 服务端要先把查询结果渲染成 xlsx 再回包，耗时由数据量决定 ⇒ 退出全局 30s 超时
    //（见 `apiClient.REQUEST_TIMEOUT_MS`：超时会把"其实生成好了"报成失败）。
    timeout: 0,
  })
  const url = URL.createObjectURL(resp.data as Blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'report.xlsx'
  a.click()
  URL.revokeObjectURL(url)
}
