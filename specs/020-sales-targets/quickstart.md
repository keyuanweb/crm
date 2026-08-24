# 快速开始：团队销售目标与排行看板

## 后端

1. Flyway `V44__sales_target_user_id.sql`：sales_target 加 user_id 列 + 索引 + 唯一键调整。
2. `SalesTarget` 实体加 userId 字段；`SalesTargetResponse` 加 userId。
3. `SalesTargetService`：get/set 支持 userId（个人目标 upsert）；首页 performance 优先个人目标。
4. `TeamLeaderboardService`：按月排行（目标/赢单/达成率，按 created_by 归属）。
5. `StatsController`：GET /stats/sales-targets?userId、GET /stats/leaderboard。
6. 测试：`TeamLeaderboardServiceTest` + `SalesTargetsIT`。

## 前端

1. `types/stats.ts`：SalesTarget 加 userId、LeaderboardItem 类型。
2. `services/statsService.ts`：fetchLeaderboard、saveSalesTarget 支持 userId。
3. `TeamLeaderboardPage`：排行看板（Table + Progress 达成率 + 红黄绿 Tag）。
4. `App.tsx`：注册排行路由（数据分析分组）。
5. `DashboardPage`：业绩达成卡片展示个人目标（含 personal 标识）。

## 验证

- 后端：`mvn test`（新增测试，不影响既有 217）。
- 前端：`pnpm run typecheck` + `lint` + `test`。
- 手动：设置销售 A/B 个人目标 → 排行看板按达成率排序 → 首页业绩达成显示个人目标。
