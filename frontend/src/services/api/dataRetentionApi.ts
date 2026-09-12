/** 数据保留策略 API 客户端（080-data-retention）。 */

import { apiClient } from '../apiClient';
import type { DataRetentionExecutionResponse, DataRetentionPolicyRequest, DataRetentionPolicyResponse } from '../../types/dataRetention';

/** 相对路径——`apiClient` 已带 `baseURL: '/api/v1'`（改造前这里独立拼全路径、用裸 `fetch` 直发）。 */
const API_BASE = '/data-retention';

/**
 * 后端这几个端点返回的是**裸响应体**（`ResponseEntity<T>`），不是全站信封 `{ success, data, error }`。
 *
 * 故取一层 `.data`（axios 的响应体）即止，**不能**再取 `.data.data` —— 多解一层会把页面从
 * "401 空表"变成"解析失败"（FR-G17、research.md §9）。
 */
export const dataRetentionApi = {
  /** 创建策略。 */
  createPolicy: async (data: DataRetentionPolicyRequest) =>
    (await apiClient.post<DataRetentionPolicyResponse>(`${API_BASE}/policies`, data)).data,

  /** 获取所有策略。 */
  getAllPolicies: async () =>
    (await apiClient.get<DataRetentionPolicyResponse[]>(`${API_BASE}/policies`)).data,

  /** 获取策略详情。 */
  getPolicy: async (id: number) =>
    (await apiClient.get<DataRetentionPolicyResponse>(`${API_BASE}/policies/${id}`)).data,

  /** 更新策略。 */
  updatePolicy: async (id: number, data: DataRetentionPolicyRequest) =>
    (await apiClient.put<DataRetentionPolicyResponse>(`${API_BASE}/policies/${id}`, data)).data,

  /** 删除策略。 */
  deletePolicy: async (id: number) => {
    await apiClient.delete<void>(`${API_BASE}/policies/${id}`);
  },

  /** 获取执行历史。 */
  getExecutions: async (id: number) =>
    (
      await apiClient.get<DataRetentionExecutionResponse[]>(
        `${API_BASE}/policies/${id}/executions`,
      )
    ).data,

  /** 执行归档。 */
  executeArchival: async () => {
    await apiClient.post<void>(`${API_BASE}/execute`);
  },
};
