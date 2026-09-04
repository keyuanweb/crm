# Tasks: 团队销售目标与排行看板



**Input**: Design documents from `/specs/020-sales-targets/`



**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/sales-targets.md



**Tests**: 单元测试 (JUnit 5 + Mockito) + 集成测试 (Spring Boot Test + MockMvc) + 前端测试 (Vitest + RTL)

## Phase 1: 基础设施搭建



- [x] T001 [P] 编写 Flyway 迁移脚本 `backend/src/main/resources/db/migration/V44__sales_target_user_id.sql`，为 sales_target 表添加 `user_id` 列，创建索引 `idx_sales_target_user_month`（联合唯一约束：user_id + target_month，user_id 为 NULL 表示全局目标）。
- [x] T002 [P] 修改 `entity/SalesTarget.java` 添加 `userId` 字段；修改 `dto/stats/SalesTargetResponse.java` 添加 `userId` 字段。

## Phase 2: 后端测试（测试优先）

- [x] T003 [P] [US1] 编写单元测试 `backend/src/test/java/com/crm/service/TeamLeaderboardServiceTest.java`，测试按月统计每位销售赢单金额（按 sales_opportunity.created_by 归属当月 CLOSED_WON 金额），验证 TeamLeaderboardService 正确计算目标/赢单/达成率，支持按达成率或赢单金额排序。
- [x] T004 [P] [US1] 编写集成测试 `backend/src/test/java/com/crm/integration/SalesTargetsIT.java`，测试个人目标设置/查询接口和排行接口 GET /stats/leaderboard 的数据权限过滤（ADMIN 可见全部，SALES 仅见自己）。

## Phase 3: 后端聚合服务实现（US1 个人目标）

- [x] T005 [US1] 修改 `SalesTargetService` 添加 `get/set` 方法支持 `userId`，实现按 (userId, month) 查询或 upsert；`get` 方法优先查询个人目标（userId 非 NULL），未设置时回退全局目标（user_id IS NULL）。依赖 T001/T002。
- [x] T006 [US1] 修改 `StatsController` 添加 `GET /stats/sales-targets` 和 `PUT /stats/sales-targets` 方法支持 `userId` 参数；添加 `SalesTargetRequest` DTO 包含 `userId` 字段。依赖 T005。

## Phase 4: 后端排行服务实现（US2 团队排行）

- [x] T007 [US2] 编写 `service/TeamLeaderboardService.java`，实现按月聚合查询：连接 sales_target 表和赢单金额（当月 CLOSED_WON 金额，按 created_by 归属），返回 LeaderboardItem 列表（目标/赢单/达成率），支持按达成率或赢单金额排序，遵循数据权限（SALES 仅看自己，ADMIN 看全部）。依赖 T001/T002。
- [x] T008 [US2] 编写 `dto/stats/LeaderboardItem.java`；修改 StatsController 添加 `GET /stats/leaderboard` 接口，支持 month/sortBy 参数。依赖 T007。
- [x] T009 [US2] 修改 `DashboardStatsService.computePerformance` 方法，优先展示个人目标与达成率（user_id=当前用户），未设置时回退全局目标；返回 `Performance` 对象包含 `personal` 字段。依赖 T005。

## Phase 5: 前端实现

- [x] T010 [P] [US1] 修改 `types/stats.ts` 添加 LeaderboardItem 类型和 SalesTarget.userId 字段；修改 `services/statsService.ts` 添加 `fetchLeaderboard(month)` 和 `saveSalesTarget` 方法支持 userId。
- [x] T011 [US2] 编写 `pages/stats/TeamLeaderboardPage.tsx`，实现团队排行看板页面：显示每位销售的目标、赢单金额、达成率，使用 antd Table + Progress + Tag 展示红黄绿标识；添加路由到 `App.tsx`。
- [x] T012 [US3] 修改 `DashboardPage` 业绩达成卡片，优先展示个人目标与达成率（`Performance.personal`），未设置时显示全局目标。

## Phase 6: 质量检查

- [x] T013 运行 `mvn test` 确保后端测试通过（TeamLeaderboardServiceTest + SalesTargetsIT）。
- [x] T014 运行 `pnpm run typecheck` + `lint` + `test` 确保前端代码质量。
- [x] T015 [P] 人工审查：个人目标设置/查询正确、团队排行显示正确、首页业绩达成卡片正确、数据权限隔离正确。

## Dependencies & Execution Order



- T001/T002 是 Phase 1 基础设施，无依赖。
- T003/T004 依赖 Phase 1 完成（依赖 T001/T002）。
- T005 依赖 T001/T002。
- T006 依赖 T005。
- T007 依赖 T001/T002。
- T008 依赖 T007。
- T009 依赖 T005。
- T010/T011/T012 是 Phase 5 前端实现，依赖后端接口完成。
- Phase 6 质量检查在所有任务完成后执行。

## Notes



- sales_target.user_id NULL 表示全局目标（兼容 006 旧数据）。
- 赢单金额按 sales_opportunity.created_by 归属当月 CLOSED_WON。
- 达成率颜色阈值：达成率 <50% 红 / 50-79% 黄 / ≥80% 绿。
- 排行看板为实时聚合查询，数据量小无需缓存。
