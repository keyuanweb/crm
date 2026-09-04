# 数据模型：酷炫数据大屏（Data Vision）

**功能分支**: `071-data-vision`

**创建日期**: 2026-08-29

## 数据模型概述

本功能完全复用现有 KPI 大屏 API（`/api/v1/kpi-board`），不新增任何后端数据模型或数据库表。所有数据可视化效果在前端实现。

## 现有数据模型复用

### KpiBoard API 响应结构

**接口**: `GET /api/v1/kpi-board`

**响应体**:

```typescript
interface KpiBoard {
  /** 核心 KPI 指标 */
  kpi: KpiMetrics
  /** 销售漏斗各阶段 */
  funnel: FunnelData
  /** 团队排行 */
  leaderboard: LeaderboardItem[]
  /** 客户健康度分布 */
  healthDistribution: HealthDistribution
  /** 智能建议摘要 */
  suggestions: SuggestionSummary
  /** 近 30 天商机金额趋势 */
  trend: TrendPoint[]
}

interface KpiMetrics {
  /** 商机总数 */
  opportunityCount: number
  /** 商机金额合计 */
  amountTotal: number
  /** 赢单率 */
  winRate: number
  /** 客户总数 */
  customerCount: number
  /** 活跃客户数 */
  activeCustomerCount: number
  /** 本月新增客户数 */
  newCustomersThisMonth: number
}

interface FunnelData {
  stages: FunnelStage[]
}

interface FunnelStage {
  /** 阶段标识 */
  stage: string
  /** 商机数量 */
  count: number
  /** 金额合计 */
  amountTotal: number
  /** 转化率（可选） */
  conversionRate?: number
}

interface LeaderboardItem {
  /** 用户 ID */
  userId: number
  /** 显示名 */
  displayName: string
  /** 赢单金额 */
  wonAmount: number
  /** 达成率（可选） */
  achievementRate?: number
}

interface HealthDistribution {
  /** 风险客户数 */
  red: number
  /** 关注客户数 */
  yellow: number
  /** 健康客户数 */
  green: number
}

interface SuggestionSummary {
  /** 流失预警客户数 */
  atRiskCustomers: number
  /** 停滞商机数 */
  stalledOpportunities: number
  /** 待跟进客户数 */
  followUpCustomers: number
  /** 高分线索数 */
  highScoreLeads: number
}

interface TrendPoint {
  /** 日期 */
  date: string
  /** 金额 */
  amount: number
}
```

## 前端新增数据模型

### 组件内部状态

#### ParticleBackground 组件

```typescript
interface Particle {
  x: number
  y: number
  vx: number
  vy: number
  radius: number
  opacity: number
}

interface ParticleBackgroundProps {
  /** 粒子数量（根据屏幕分辨率动态调整） */
  particleCount?: number
  /** 连线最大距离 */
  maxLinkDistance?: number
  /** 粒子颜色 */
  particleColor?: string
  /** 连线颜色 */
  linkColor?: string
}
```

#### CountUp 组件

```typescript
interface CountUpProps {
  /** 目标数值 */
  value: number
  /** 动画持续时间（毫秒） */
  duration?: number
  /** 小数位数 */
  decimals?: number
  /** 数字格式化函数 */
  formatter?: (value: number) => string
  /** 动画完成回调 */
  onComplete?: () => void
}
```

#### ResponsiveGrid 组件

```typescript
interface ResponsiveGridProps {
  /** 1920×1080 布局的列数 */
  columns1080?: number
  /** 2560×1440 布局的列数 */
  columns1440?: number
  /** 3840×2160 布局的列数 */
  columns2160?: number
  /** 子元素 */
  children: React.ReactNode
}
```

## 数据流

```
┌─────────────────┐
│  KpiBoardPage   │
│  (数据大屏页面)   │
└────────┬────────┘
         │
         │ fetchKpiBoard()
         │
         ▼
┌─────────────────┐
│  kpiBoardService │
│  (API 服务)       │
└────────┬────────┘
         │
         │ GET /api/v1/kpi-board
         │
         ▼
┌─────────────────┐
│  Backend API    │
│  (KpiController) │
└────────┬────────┘
         │
         │ 查询数据库
         │
         ▼
┌─────────────────┐
│  MySQL Database │
│  (现有表)        │
└─────────────────┘
```

## 缓存策略

- **前端缓存**: 使用 React Query 缓存，默认 staleTime 为 30 秒。
- **后端缓存**: 复用现有 KPI API 的缓存策略（如有）。
- **数据刷新**: 每 30 秒自动刷新，手动刷新按钮可立即刷新。

## 权限控制

- **认证**: 用户必须登录才能访问数据大屏页面。
- **数据权限**: 复用现有 KPI API 的数据权限控制（ADMIN/SELF/DEPT）。
- **全屏权限**: 使用浏览器 Fullscreen API，无需额外权限。

## 安全考虑

- **XSS 防护**: 所有用户输入数据经过转义后展示。
- **CSRF 防护**: 复用现有 Spring Security CSRF 防护。
- **CORS 配置**: 复用现有 CORS 配置。
- **数据脱敏**: 不展示敏感信息（如密码、token）。

## 结论

本功能完全复用现有 KPI 大屏 API 和数据模型，不新增任何后端数据模型或数据库表。所有数据可视化效果在前端实现，包括粒子动画背景、流光边框效果、霓虹文字标题、数字滚动动画、ECharts 图表动画等。
