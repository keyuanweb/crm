/** KPI 大屏数据类型定义 — 复用项目现有类型 */
export type {
  KpiBoard,
  KpiSummary as KpiMetrics,
  FunnelStageStat as FunnelStage,
  FunnelData,
  HealthDistribution,
  TrendPoint,
} from '../../types/kpiBoard'

// LeaderboardItem 和 SuggestionSummary 从各自模块导入
export type { LeaderboardItem } from '../../types/stats'
export type { SuggestionSummary } from '../../types/suggestion'

/** 粒子类型 */
export interface Particle {
  x: number
  y: number
  vx: number
  vy: number
  radius: number
  opacity: number
}

/** 响应式布局配置 */
export interface ResponsiveConfig {
  columns1080: number
  columns1440: number
  columns2160: number
  columns: number
  fontSizeMultiplier: number
}
