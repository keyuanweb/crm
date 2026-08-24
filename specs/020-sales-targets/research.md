# 研究：团队目标与排行设计

## R1 个人目标数据模型

**决策**: `sales_target` 表加 `user_id`（BIGINT，可空）。`user_id IS NULL` = 全局目标（006 既有），`user_id = N` = 销售 N 的个人目标。唯一约束从"非删除记录按月唯一"改为"非删除记录按 (user_id, target_month) 唯一"（user_id 为 NULL 时按 target_month）。

**兼容**: 既有代码 `findByMonth(month)` 查询全局目标（user_id IS NULL）；新查询个人目标带 userId。

## R2 赢单归属

**决策**: 当月 CLOSED_WON 销售机会按 `created_by` 归属销售。`SalesOpportunity.createdBy` 在创建时记录操作人。统计：`SELECT created_by, SUM(amount) FROM sales_opportunity WHERE stage='CLOSED_WON' AND closed_at BETWEEN 月初 AND 月末 GROUP BY created_by`（用 Mapper selectList + 内存分组，复用现有 Mapper 模式）。

## R3 排行计算

**决策**: `TeamLeaderboardService.leaderboard(month)`：
1. 查当月所有 sales_target（含个人+全局）。
2. 查当月 CLOSED_WON 按 created_by 汇总金额。
3. 对每位有目标的销售（或所有销售）生成 LeaderboardItem{userId, displayName, targetAmount, wonAmount, achievementRate}。
4. 未设目标的销售：wonAmount 照算，targetAmount=null（前端显示"未设目标"）。
5. 排序：按 achievementRate 降序（无目标排最后）。
6. 数据权限：ADMIN 全量；SALES 仅自己。

## R4 首页联动

**决策**: `GET /stats/dashboard` 的 performance 区块：优先查当前用户个人目标（user_id=当前用户），未设置回退全局目标。DashboardPage 业绩达成卡片逻辑不变（数据源升级）。

## R5 达成率颜色

**决策**: <50% 红 / 50-79% 黄 / ≥80% 绿（阈值可配置 `crm.stats.achievement-thresholds`）。前端 Tag + Progress 展示。
