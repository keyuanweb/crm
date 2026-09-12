/** 销售配额 API 客户端（078-sales-quota）。 */

import { apiClient } from '../apiClient';

/**
 * 相对路径——`apiClient` 已带 `baseURL: '/api/v1'`。
 *
 * 后端这些端点返回**裸响应体**（非全站信封），故取一层 axios 的 `.data` 即止，
 * **不能**再取 `.data.data` —— 多解一层会把页面从"401 空表"变成"解析失败"（FR-G17、research.md §9）。
 */
const API_BASE = '/sales-quota';

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

export interface SalesQuotaSummary {
  totalQuota: number;
  totalActual: number;
  achievementRate: number;
}

/** 配额分解行（服务端 GET /{id}/breakdown 返回 Map，键名由 Service 手工放入，保持 camelCase）。 */
export interface SalesQuotaBreakdownItem {
  id: number;
  parentQuotaId: number;
  childQuotaId: number;
  amount: number;
  childQuota?: SalesQuotaResponse;
}

/** 配额版本记录（服务端 GET /{id}/versions 返回 Map，键名同上）。 */
export interface SalesQuotaVersionResponse {
  id: number;
  quotaId: number;
  oldAmount: number;
  newAmount: number;
  changedBy: number;
  changedAt: string;
  changeReason: string;
  versionNumber: number;
}

/**
 * 团队排名行的线上形态（GET /ranking）。
 *
 * ⚠️ 键名是 SQL 别名原文，**不是** camelCase：该端点的返回类型是 `List<Map<String, Object>>`（原始 SQL），
 * 而 MyBatis 对 Map 结果不做下划线转驼峰——`MapWrapper.findProperty` 原样返回列名，
 * `map-underscore-to-camel-case: true` 只影响实体映射（见 mybatis 3.5.15 反编译）。
 * 此处如实描述线上形态，再由 getTeamRanking 转成 camelCase，避免「接口类型写得好看、运行时全是 undefined」。
 */
interface TeamRankingRow {
  teamId: number;
  teamName: string;
  quota_amount: number;
  actual_amount: number;
  achievement_rate: number;
}

/** 团队排名行（对调用方的形态）。 */
export interface TeamRankingItem {
  teamId: number;
  teamName: string;
  quotaAmount: number;
  actualAmount: number;
  achievementRate: number;
}

/**
 * 序列化查询串：跳过 null/undefined/空串，否则会发出 `status=undefined` 这种字面量参数。
 *
 * **刻意保留而不用 axios 的 `params`**：axios 只跳过 `null`/`undefined`，**会**把空串发成
 * `status=` —— 后端对"空串"与"未传"的处理未必相同。这段过滤是既有行为，改造只换传输层，
 * 不顺手改变已发出的请求形状（FR-G17 的保形要求同样适用于查询串）。
 */
function buildQuery(params: Record<string, unknown>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue;
    search.append(key, String(value));
  }
  return search.toString();
}

export const quotaApi = {
  /** 创建配额。 */
  create: async (data: SalesQuotaRequest) =>
    (await apiClient.post<SalesQuotaResponse>(API_BASE, data)).data,

  /** 获取配额列表。 */
  list: async (params: {
    page?: number;
    size?: number;
    year?: number;
    teamId?: number;
    userId?: number;
    status?: string;
  }) =>
    (
      await apiClient.get<{
        records: SalesQuotaResponse[];
        total: number;
        size: number;
        current: number;
        pages: number;
      }>(`${API_BASE}?${buildQuery(params)}`)
    ).data,

  /** 获取配额详情。 */
  detail: async (id: number) =>
    (await apiClient.get<SalesQuotaResponse>(`${API_BASE}/${id}`)).data,

  /** 更新配额。 */
  update: async (id: number, data: SalesQuotaRequest) =>
    (await apiClient.put<SalesQuotaResponse>(`${API_BASE}/${id}`, data)).data,

  /**
   * 分解配额。
   *
   * 改造前此方法在**成功的空响应体**上也会抛错（`response.json()` 对空体抛
   * `Unexpected end of JSON input`），故"分解成功却弹失败提示"是可复现的既有缺陷；
   * 换用 axios 后空体为 `''`，不再抛错——这是修复，不是行为变更。
   */
  breakdown: async (id: number, breakdowns: SalesQuotaBreakdownRequest[]) => {
    await apiClient.post<void>(`${API_BASE}/${id}/breakdown`, breakdowns);
  },

  /** 获取配额分解。 */
  getBreakdown: async (id: number) =>
    (await apiClient.get<SalesQuotaBreakdownItem[]>(`${API_BASE}/${id}/breakdown`)).data,

  /** 获取配额达成率。 */
  getAchievement: async (id: number) =>
    (await apiClient.get<SalesQuotaAchievementResponse>(`${API_BASE}/${id}/achievement`)).data,

  /** 获取配额版本历史。 */
  getVersions: async (id: number) =>
    (await apiClient.get<SalesQuotaVersionResponse[]>(`${API_BASE}/${id}/versions`)).data,

  /** 获取团队排名（线上为 snake_case，在此转为 camelCase）。 */
  getTeamRanking: async (year: number): Promise<TeamRankingItem[]> => {
    const rows = (
      await apiClient.get<TeamRankingRow[]>(`${API_BASE}/ranking`, { params: { year } })
    ).data;
    return rows.map((row) => ({
      teamId: row.teamId,
      teamName: row.teamName,
      quotaAmount: row.quota_amount,
      actualAmount: row.actual_amount,
      achievementRate: row.achievement_rate,
    }));
  },

  /** 获取年度配额汇总。 */
  getSummary: async (year: number) =>
    (await apiClient.get<SalesQuotaSummary>(`${API_BASE}/summary`, { params: { year } })).data,
};
