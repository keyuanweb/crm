/** 销售配额 API 客户端（078-sales-quota）。 */

const API_BASE = '/api/v1/sales-quota';

export interface SalesQuotaRequest {
  parentId?: number;
  quarter?: number;
  year: number;
  teamId?: number;
  userId?: number;
  amount: number;
  periodStart: string;
  periodEnd: string;
  changeReason?: string;
}

export interface SalesQuotaResponse {
  id: number;
  parentId?: number;
  quarter?: number;
  year: number;
  teamId?: number;
  userId?: number;
  amount: number;
  status: string;
  periodStart: string;
  periodEnd: string;
  actualAmount?: number;
  achievementRate?: number;
  createdAt: string;
  teamName?: string;
  userName?: string;
}

export interface SalesQuotaBreakdownRequest {
  quarter?: number;
  teamId?: number;
  userId?: number;
  amount: number;
}

export interface SalesQuotaAchievementResponse {
  quotaId: number;
  quotaAmount: number;
  actualAmount: number;
  achievementRate: number;
  calculatedAt: string;
  status: 'ON_TRACK' | 'AT_RISK' | 'BELOW_TARGET';
}

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

export const quotaApi = {
  /** 创建配额。 */
  create: (data: SalesQuotaRequest) =>
    request<SalesQuotaResponse>(`${API_BASE}`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  /** 获取配额列表。 */
  list: (params: {
    page?: number;
    size?: number;
    year?: number;
    teamId?: number;
    userId?: number;
    status?: string;
  }) =>
    request<{
      content: SalesQuotaResponse[];
      totalElements: number;
      totalPages: number;
      size: number;
      number: number;
    }>(`${API_BASE}?${new URLSearchParams(params as any).toString()}`),

  /** 获取配额详情。 */
  detail: (id: number) => request<SalesQuotaResponse>(`${API_BASE}/${id}`),

  /** 更新配额。 */
  update: (id: number, data: SalesQuotaRequest) =>
    request<SalesQuotaResponse>(`${API_BASE}/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  /** 分解配额。 */
  breakdown: (id: number, breakdowns: SalesQuotaBreakdownRequest[]) =>
    request<void>(`${API_BASE}/${id}/breakdown`, {
      method: 'POST',
      body: JSON.stringify(breakdowns),
    }),

  /** 获取配额分解。 */
  getBreakdown: (id: number) =>
    request<any[]>(`${API_BASE}/${id}/breakdown`),

  /** 获取配额达成率。 */
  getAchievement: (id: number) =>
    request<SalesQuotaAchievementResponse>(`${API_BASE}/${id}/achievement`),

  /** 获取配额版本历史。 */
  getVersions: (id: number) =>
    request<any[]>(`${API_BASE}/${id}/versions`),

  /** 获取团队排名。 */
  getTeamRanking: (year: number) =>
    request<any[]>(`${API_BASE}/ranking?year=${year}`),
};
