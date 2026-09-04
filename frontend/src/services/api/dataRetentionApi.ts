/** 数据保留策略 API 客户端（080-data-retention）。 */

import type { DataRetentionExecutionResponse, DataRetentionPolicyRequest, DataRetentionPolicyResponse } from '../../types/dataRetention';

const API_BASE = '/api/v1/data-retention';

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

export const dataRetentionApi = {
  /** 创建策略。 */
  createPolicy: (data: DataRetentionPolicyRequest) =>
    request<DataRetentionPolicyResponse>(`${API_BASE}/policies`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  /** 获取所有策略。 */
  getAllPolicies: () =>
    request<DataRetentionPolicyResponse[]>(`${API_BASE}/policies`),

  /** 获取策略详情。 */
  getPolicy: (id: number) =>
    request<DataRetentionPolicyResponse>(`${API_BASE}/policies/${id}`),

  /** 更新策略。 */
  updatePolicy: (id: number, data: DataRetentionPolicyRequest) =>
    request<DataRetentionPolicyResponse>(`${API_BASE}/policies/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  /** 删除策略。 */
  deletePolicy: (id: number) =>
    request<void>(`${API_BASE}/policies/${id}`, {
      method: 'DELETE',
    }),

  /** 获取执行历史。 */
  getExecutions: (id: number) =>
    request<DataRetentionExecutionResponse[]>(`${API_BASE}/policies/${id}/executions`),

  /** 执行归档。 */
  executeArchival: () =>
    request<void>(`${API_BASE}/execute`, {
      method: 'POST',
    }),
};
