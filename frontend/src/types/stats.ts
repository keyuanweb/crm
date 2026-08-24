export interface StageStat {
  stage: string
  count: number
  amountTotal: number
}

export interface PipelineStats {
  stages: StageStat[]
  grandTotal: { count: number; amountTotal: number }
  generatedAt: string
}

// ===== 006 销售仪表盘 =====

export interface DashboardSummary {
  opportunityCount: number
  amountTotal: number
  winRate: number
  customerCount: number
  activeCustomerCount: number
  newCustomersThisMonth: number
}

export interface FunnelStageStat {
  stage: string
  count: number
  amountTotal: number
  conversionRate?: number | null
}

export interface DashboardFunnel {
  stages: FunnelStageStat[]
  grandTotal: { stage: string; count: number; amountTotal: number; conversionRate?: null }
}

export interface ForecastItem {
  stage: string
  amount: number
  probability: number
  weighted: number
  /** 概率来源：HISTORICAL / DEFAULT / FIXED（019 预测校准）。 */
  probabilitySource?: string
}

export interface DashboardForecast {
  weightedAmount: number
  breakdown: ForecastItem[]
}

export interface DashboardPerformance {
  month: string
  targetAmount?: number
  wonAmount?: number
  achievementRate?: number
  configured: boolean
  /** 是否个人目标（020）。 */
  personal?: boolean
}

export interface MethodStat {
  method: string
  count: number
}

export interface RecentFollowUp {
  id: number
  method: string
  content: string
  customerName?: string
  followUpBy?: string
  createdAt: string
}

export interface DashboardFollowUps {
  total: number
  byMethod: MethodStat[]
  recent: RecentFollowUp[]
}

export interface StalledOpportunity {
  id: number
  opportunityName?: string
  customerName?: string
  amount?: number
  stage: string
  stalledDays: number
  lastUpdatedAt?: string
}

export interface DashboardStats {
  summary: DashboardSummary
  funnel: DashboardFunnel
  forecast: DashboardForecast
  performance: DashboardPerformance
  followUps: DashboardFollowUps
  stalledOpportunities: StalledOpportunity[]
  generatedAt: string
}

export interface SalesTarget {
  month: string
  targetAmount?: number
  /** 归属用户（undefined=全局目标，020）。 */
  userId?: number
  createdBy?: number
  updatedAt?: string
}

export interface SalesTargetPayload {
  month: string
  targetAmount: number
  userId?: number
}

/** 团队排行项（020）。 */
export interface LeaderboardItem {
  userId: number
  displayName: string
  targetAmount?: number
  wonAmount: number
  achievementRate?: number
}

export interface LeaderboardResult {
  items: LeaderboardItem[]
  month: string
}
