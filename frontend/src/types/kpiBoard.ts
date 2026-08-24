/** KPI 大屏（023）。 */
import type { LeaderboardItem } from './stats'
import type { SuggestionSummary } from './suggestion'

export interface KpiSummary {
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

export interface FunnelGrand {
  stage: string
  count: number
  amountTotal: number
  conversionRate?: number | null
}

export interface FunnelData {
  stages: FunnelStageStat[]
  grandTotal: FunnelGrand
}

export interface HealthDistribution {
  red: number
  yellow: number
  green: number
}

export interface TrendPoint {
  date: string
  count: number
  amount: number
}

export interface KpiBoard {
  kpi: KpiSummary
  funnel: FunnelData
  leaderboard: LeaderboardItem[]
  healthDistribution: HealthDistribution
  suggestions: SuggestionSummary
  trend: TrendPoint[]
}
