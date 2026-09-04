# Quickstart: 大屏数据看板（KPI 大屏）



## 后端



1. `KpiBoardService`：组合 DashboardStatsService + TeamLeaderboardService + SuggestionService + 健康度分布（抽查 200 客户）+ 30 天趋势（SalesOpportunityMapper），Redis 缓存 5 分钟。

2. `StatsController` 新增 `GET /stats/kpi-board`（仅 ADMIN）。

3. 测试：`KpiBoardServiceTest` + `KpiBoardIT`。



## 前端



1. `types/kpiBoard.ts` + `services/kpiBoardService.ts`（fetchKpiBoard）。

2. `KpiBoardPage`：全屏深色大屏（KPI 卡 + 漏斗 + 排行 TopN + 健康度分布 + 建议摘要 + SVG 趋势），自动刷新 60s + 全屏切换。

3. `App.tsx`：注册大屏路由（数据分析分组）。



## 验证



- 后端：`mvn test`（新增测试，不影响既有 228）。

- 前端：`pnpm run typecheck` + `lint` + `test`。

- 手动：管理员打开大屏，全屏展示，60s 自动刷新。

