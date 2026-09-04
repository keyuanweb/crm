# Tasks: 大屏数据看板（KPI 大屏）
**Input**: Design documents from `/specs/023-kpi-dashboard/`



**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/kpi-board.md



**Tests**: 单元测试（KpiBoardServiceTest）、集成测试（KpiBoardIT）、前端渲染测试（KpiBoardPage.test.tsx）

## Phase 1: 基础设施搭建



- [x] T001 [P] 创建聚合 DTO：`dto/stats/KpiBoardResponse.java` 包含 pi/funnel/leaderboard/healthDistribution/suggestions/trend 字段；`dto/stats/HealthDistribution.java` 包含 ed/yellow/green 字段；`dto/stats/TrendPoint.java` 包含 ate/count/amount 字段

## Phase 2: 后端测试

- [x] T002 [P] [US1] 创建单元测试：`backend/src/test/java/com/crm/service/KpiBoardServiceTest.java` 测试聚合服务组合逻辑正确性，验证 DashboardStatsService/TeamLeaderboardService/SuggestionService/CustomerService 组合输出 KpiBoardResponse 数据一致性；验证 Redis 缓存 key `stats:kpi-board` 5 分钟过期

- [x] T003 [P] [US1] 创建集成测试：`backend/src/test/java/com/crm/integration/KpiBoardIT.java` 测试聚合接口 GET /stats/kpi-board 返回完整数据（pi/funnel/leaderboard/healthDistribution/suggestions/trend）；验证 SALES 用户访问返回 403 禁止访问

## Phase 3: 后端聚合服务实现

- [x] T004 [US1] 创建聚合服务：`service/KpiBoardService.java` 组合 DashboardStatsService/TeamLeaderboardService/SuggestionService/CustomerService 实现健康度分布统计（最多 200 个客户）；SalesOpportunityMapper 近 30 天商机金额趋势；输出 KpiBoardResponse；Redis 缓存 5 分钟（key `stats:kpi-board`）

- [x] T005 [US2] 修改 StatsController` 注册端点 `GET /stats/kpi-board`，添加 PreAuthorize ADMIN 权限校验

## Phase 4: 前端实现

- [x] T006 [P] [US1] 创建前端类型和服务：`types/kpiBoard.ts` + `services/kpiBoardService.ts` 定义 fetchKpiBoard 接口

- [x] T007 [US1] 创建大屏页面：`pages/board/KpiBoardPage.tsx` 实现全屏深色风格组件，包含 KPI 指标卡 + 销售漏斗 + 团队排行 TopN + 健康度分布饼图 + 智能建议摘要 + SVG 折线图；60s 自动刷新 + 全屏切换

- [x] T008 修改 `App.tsx` 注册大屏路由（数据分析分组）

## Phase 5: 质量检查

- [x] T009 后端测试：`mvn test` 验证单元测试 + 集成测试通过（KpiBoardServiceTest + KpiBoardIT）

- [x] T010 前端测试：`pnpm run typecheck` + `lint` + `test` 验证类型检查和代码质量

- [x] T011 [P] 人工审查：验证大屏数据与既有统计接口一致性；验证自动刷新 60s 功能；验证 SALES 用户禁止访问

## Dependencies & Execution Order



- T001 必须最先完成
- T002/T003 依赖 T001 完成
- T004 依赖 T001 完成
- T005 依赖 T004 完成
- T006/T007 依赖 T004 完成
- Phase 5 在 Phase 3/4 完成后执行

## Notes


- 聚合服务复用既有 DashboardStatsService/TeamLeaderboardService/SuggestionService 逻辑，不重复计算
- 健康度分布基于 018 客户健康度，最多抽查 200 个客户
- 近 30 天趋势基于 SalesOpportunityMapper 按日聚合
- 大屏仅 ADMIN 可访问；Redis 缓存 5 分钟
- 前端使用自绘 SVG 图表，不引入 echarts（YAGNI 原则）
