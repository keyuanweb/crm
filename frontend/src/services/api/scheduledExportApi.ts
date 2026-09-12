/** 定时导出任务 API 客户端（079-scheduled-export）。 */

import { apiClient } from '../apiClient';
import type { ScheduledExportExecutionResponse, ScheduledExportRequest, ScheduledExportResponse } from '../../types/scheduledExport';

/**
 * 相对路径——`apiClient` 已带 `baseURL: '/api/v1'`。
 *
 * 改造前这里独立拼 `/api/v1/...` 并用裸 `fetch` 直发，凭空绕开了全站客户端的
 * 凭据注入（它读 `localStorage.getItem('token')`，而全站写入的键是 `accessToken`，
 * 故实际发出的请求头是 `Authorization: Bearer null`）、401 跳转与错误信封解析。
 */
const API_BASE = '/scheduled-exports';

/**
 * 后端这几个端点返回的是**裸响应体**（`ResponseEntity<T>`），不是全站信封 `{ success, data, error }`。
 *
 * 故这里**不能**像 `announcementService` 那样再取一层 `.data.data` —— 多解一层会把每个
 * 页面从"401 空表"变成"解析失败"，是本次改造最容易踩的坑（FR-G17、research.md §9）。
 */
export const scheduledExportApi = {
  /** 创建定时导出任务。 */
  create: async (data: ScheduledExportRequest) =>
    (await apiClient.post<ScheduledExportResponse>(API_BASE, data)).data,

  /**
   * 获取定时导出任务列表。
   *
   * `userId` 只作一致性断言：服务端按登录身份过滤，参数值与登录用户不符会被拒（FR-G16）。
   */
  list: async (userId: number) =>
    (await apiClient.get<ScheduledExportResponse[]>(API_BASE, { params: { userId } })).data,

  /** 获取定时导出任务详情。 */
  detail: async (id: number) =>
    (await apiClient.get<ScheduledExportResponse>(`${API_BASE}/${id}`)).data,

  /** 更新定时导出任务状态。 */
  updateStatus: async (id: number, status: string) => {
    await apiClient.put<void>(`${API_BASE}/${id}/status`, null, { params: { status } });
  },

  /** 删除定时导出任务。 */
  delete: async (id: number) => {
    await apiClient.delete<void>(`${API_BASE}/${id}`);
  },

  /** 获取执行历史。 */
  getExecutions: async (id: number) =>
    (
      await apiClient.get<ScheduledExportExecutionResponse[]>(
        `${API_BASE}/${id}/executions`,
      )
    ).data,

  /** 手动立即执行。 */
  executeNow: async (id: number) => {
    await apiClient.post<void>(`${API_BASE}/${id}/execute-now`);
  },
};
