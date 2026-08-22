# Implementation Plan: 销售仪表盘模块

**Branch**: `006-sales-dashboard` | **Date**: 2026-08-22 | **Spec**: [spec.md](./spec.md)

## Summary

新增销售仪表盘聚合视图：核心指标卡（商机总数/金额合计/赢单率/本月新增客户）、销售漏斗（阶段数量+金额+转化率）、销售预测（阶段概率加权）、业绩达成（月度目标 vs 已赢金额）、客户分析（总数/活跃/本月新增）、跟进活动报表（方式分布+最近记录）与停滞商机预警（超 N 天未更新）。后端新增 `SalesTarget` 实体（月度目标，管理员设置）与 `DashboardStats` 聚合接口（Redis 缓存 5 分钟，写操作后失效），前端新增仪表盘页（复用既有 ProLayout 菜单与 antd 卡片/表格）。

## Technical Context

**Language/Version**: Java 17 / TypeScript 5（沿用既有技术栈）

**Primary Dependencies**: MyBatis-Plus、Redis（缓存）、AuditService（审计，目标设置）、既有 OpportunityStatsService

**Storage**: MySQL 新增 `sales_target` 表（Flyway V11 迁移）；其余指标从既有表实时聚合

**Testing**: JUnit 5 + Spring Boot Test（SalesTargetServiceTest 单元、DashboardStatsServiceTest 单元、SalesTargetIT / DashboardStatsIT 集成）

**Target Platform**: Web（前端仪表盘页，antd Card/Table/Statistic）

**Project Type**: 现有 Web 应用新增模块

**Performance Goals**: 仪表盘首屏 ≤2s（SC-D01，缓存命中后应远低于此）

**Constraints**: 目标按月唯一；预测概率固定（20/50/100/0）；停滞阈值默认 7 天可配置；统计基于未删除数据；缓存 ≤5 分钟

**Scale/Scope**: 销售机会/客户/跟进数量级 ≤ 数千，单次聚合查询可承受；仪表盘为只读聚合

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | Controller→Service→持久层 | ✅ 满足（复用既有分层，聚合逻辑在 Service） |
| 原则三：数据完整性、安全与校验 | DTO 校验、服务端授权 | ✅ 满足（目标设置管理员授权 + Bean Validation） |
| 原则四：测试优先与质量门禁 | 测试先于实现、合并门禁 | ✅ 满足（单元/集成测试 + 既有门禁） |
| 原则五：简洁、可维护与可观测 | 无 N+1、缓存一致性 | ✅ 满足（批量聚合查询 + Redis 缓存失效） |

**结论**: 无门禁违规。

## Project Structure

```text
specs/006-sales-dashboard/
├── spec.md / plan.md / tasks.md / research.md / data-model.md / quickstart.md
├── contracts/（stats.md 扩展：dashboard / sales-targets）

backend/src/main/java/com/crm/
├── entity/SalesTarget.java + repository/SalesTargetMapper.java
├── dto/stats/DashboardStats.java（summary/funnel/forecast/performance/followUps/stalled）
├── dto/stats/SalesTargetRequest.java + SalesTargetResponse.java
├── service/DashboardStatsService.java（聚合 + Redis 缓存）
├── service/SalesTargetService.java（月度目标 CRUD/upsert）
├── controller/StatsController.java（扩展：/dashboard、/sales-targets）
└── resources/db/migration/V11__sales_target.sql

backend/src/test/java/com/crm/
├── service/SalesTargetServiceTest.java + service/DashboardStatsServiceTest.java
└── integration/SalesTargetIT.java（或并入 DashboardStatsIT）

frontend/src/
├── types/stats.ts（扩展 DashboardStats/SalesTarget）
├── services/statsService.ts（fetchDashboardStats/fetchSalesTarget/saveSalesTarget）
├── pages/stats/DashboardPage.tsx（指标卡 + 漏斗 + 预测 + 达成 + 客户/跟进 + 停滞预警）
└── App.tsx（/stats 路由指向 DashboardPage）
```

**Structure Decision**: 沿用既有前后端目录结构（Option 2 Web 应用）。仪表盘聚合接口挂在既有 `StatsController` 下（同属统计域），新增 `SalesTarget` 实体与两个 Service。

## Complexity Tracking

无违规，本表留空。
