/** 定时导出任务 API 客户端（079-scheduled-export）。 */

import type { ScheduledExportExecutionResponse, ScheduledExportRequest, ScheduledExportResponse } from '../../types/scheduledExport';

const API_BASE = '/api/v1/scheduled-exports';

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const token = localStorage.getItem('token');
  const response = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      ...options?.headers,
    },
  });
  if (!response.ok) {
    const error = await response.text();
    throw new Error(error || `HTTP ${response.status}`);
  }
  return response.json();
}

export const scheduledExportApi = {
  /** 创建定时导出任务。 */
  create: (data: ScheduledExportRequest) =>
    request<ScheduledExportResponse>(API_BASE, {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  /** 获取定时导出任务列表。 */
  list: (userId: number) =>
    request<ScheduledExportResponse[]>(`${API_BASE}?userId=${userId}`),

  /** 获取定时导出任务详情。 */
  detail: (id: number) =>
    request<ScheduledExportResponse>(`${API_BASE}/${id}`),

  /** 更新定时导出任务状态。 */
  updateStatus: (id: number, status: string) =>
    request<void>(`${API_BASE}/${id}/status?status=${status}`, {
      method: 'PUT',
    }),

  /** 删除定时导出任务。 */
  delete: (id: number) =>
    request<void>(`${API_BASE}/${id}`, {
      method: 'DELETE',
    }),

  /** 获取执行历史。 */
  getExecutions: (id: number) =>
    request<ScheduledExportExecutionResponse[]>(`${API_BASE}/${id}/executions`),

  /** 手动立即执行。 */
  executeNow: (id: number) =>
    request<void>(`${API_BASE}/${id}/execute-now`, {
      method: 'POST',
    }),
};
