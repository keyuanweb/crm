# Implementation Plan: 团队销售目标与排行看板

**Branch**: `020-sales-targets` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/020-sales-targets/spec.md`

## Summary

为销售目标引入个人维度：`sales_target` 表新增 `user_id` 字段（用户+月份唯一，兼容现有全局目标），支持每位销售设置个人月度目标；新增团队排行接口，按月统计每位销售赢单金额（按销售机会创建人）与达成率，前端排行看板展示红黄绿标识，首页业绩达成卡片优先展示个人目标。

## Technical Context

**Language/Version**: Java 17（后端）+ TypeScript/React 18（前端）

**Primary Dependencies**: Spring Boot 3.2、MyBatis-Plus（SalesTargetMapper/SalesOpportunityMapper/UserMapper）、antd 5（Table/Progress/Statistic）

**Storage**: `sales_target` 表加 `user_id` 列（Flyway V44）；无新表

**Testing**: JUnit 5 + Mockito（单元）、Spring Boot Test + MockMvc（集成）、Vitest + RTL（前端）

**Target Platform**: Web

**Project Type**: Web 应用（Spring Boot 后端 + React 前端）

**Performance Goals**: 排行接口 < 200ms（实时聚合，数据量小）

**Constraints**: 兼容既有全局目标（user_id 为空=全局）；赢单归属按 sales_opportunity.created_by；不引入新依赖

**Scale/Scope**: sales_target 加列 + SalesTargetService 扩展 + 排行服务 + StatsController 扩展 + 前端排行页 + 首页联动

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则一：契约优先的 API 设计 | 端点契约先于实现 | ✅ 满足（contracts/sales-targets.md 定义个人目标与排行契约） |
| 原则二：分层架构与关注点分离 | Controller→Service→Repository | ✅ 满足（SalesTargetService 个人目标 + TeamLeaderboardService 排行） |
| 原则三：数据完整性、安全与校验 | 服务端权限 | ✅ 满足（排行按数据权限过滤；个人目标校验 user_id 存在） |
| 原则四：测试优先与质量门禁 | 测试先于实现 | ✅ 满足（SalesTargetServiceTest + TeamLeaderboardServiceTest + 集成测试 + 前端） |
| 原则五：简洁、可维护与可观测 | 无 N+1、结构化日志 | ✅ 满足（排行一次聚合；目标/排行记 DEBUG 日志） |

**结论**: 无门禁违规。

## Project Structure

### Documentation (this feature)

```text
specs/020-sales-targets/
├── plan.md / spec.md / research.md / data-model.md / quickstart.md
├── contracts/（sales-targets 契约）
└── tasks.md
```

### Source Code (repository root)

```text
backend/src/main/java/com/crm/
├── entity/SalesTarget.java                     # 修改：加 userId 字段
├── service/SalesTargetService.java             # 修改：get/set 支持 userId；个人目标优先
├── service/TeamLeaderboardService.java         # 新增：按月排行（目标/赢单/达成率）
├── controller/StatsController.java             # 修改：GET /stats/sales-targets?userId、GET /stats/leaderboard
├── dto/stats/SalesTargetResponse.java          # 修改：加 userId 字段
├── dto/stats/LeaderboardItem.java              # 新增：排行项
└── resources/db/migration/V44__sales_target_user_id.sql  # 新增：user_id 列 + 索引

backend/src/test/java/com/crm/
├── service/TeamLeaderboardServiceTest.java     # 新增：排行/达成率/排序 单元测试
└── integration/SalesTargetsIT.java             # 新增：个人目标 + 排行 集成测试

frontend/src/
├── types/stats.ts                              # 修改：SalesTarget/LeaderboardItem 类型
├── services/statsService.ts                    # 修改：fetchLeaderboard/saveSalesTarget(userId)
├── pages/stats/TeamLeaderboardPage.tsx         # 新增：团队排行看板页
├── App.tsx                                     # 修改：注册排行路由
└── pages/stats/DashboardPage.tsx               # 修改：业绩达成卡片优先个人目标
```

**Structure Decision**: 沿用既有分层。`sales_target.user_id` 为 NULL 表示全局目标（兼容 006）；个人目标查询时按 当前用户+月份 优先，未设置回退全局。排行实时聚合（按 created_by 统计当月 CLOSED_WON 金额），数据量小不缓存。

## Complexity Tracking

> 无违规，本表留空。
