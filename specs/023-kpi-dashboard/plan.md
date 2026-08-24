# Implementation Plan: 大屏数据看板（KPI 大屏）

**Branch**: `023-kpi-dashboard` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/023-kpi-dashboard/spec.md`

## Summary

新增 KPI 大屏：后端提供 `GET /stats/kpi-board` 聚合接口（复用 DashboardStatsService/TeamLeaderboardService/SuggestionService 组合输出 KPI/漏斗/排行 TopN/健康度分布/建议摘要/30 天趋势，Redis 缓存 5 分钟）；前端新增全屏深色大屏页（自动刷新 60 秒 + 全屏切换），仅 ADMIN 访问。

## Technical Context

**Language/Version**: Java 17（后端）+ TypeScript/React 18（前端）

**Primary Dependencies**: Spring Boot 3.2、MyBatis-Plus（复用现有 Mapper）、Redis（缓存）、antd 5（Statistic/Progress/Tag）+ 轻量 SVG 自绘图表（不引入 echarts，YAGNI）

**Storage**: 无新表；聚合结果 Redis 缓存 5 分钟

**Testing**: JUnit 5 + Mockito（单元）、Spring Boot Test + MockMvc（集成）、Vitest + RTL（前端）

**Target Platform**: Web（大屏分辨率 ≥1024px）

**Project Type**: Web 应用（Spring Boot 后端 + React 前端）

**Performance Goals**: 聚合接口 ≤ 1s（复用聚合 + 缓存）；自动刷新 60s

**Constraints**: 复用既有统计服务组合，不重复实现；大屏仅 ADMIN；不引入新图表库（自绘 SVG 折线/条）

**Scale/Scope**: 1 个聚合服务 + 1 个接口 + 前端大屏页 + 路由

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则一：契约优先的 API 设计 | 端点契约先于实现 | ✅ 满足（contracts/kpi-board.md 定义聚合契约） |
| 原则二：分层架构与关注点分离 | Controller→Service→Repository | ✅ 满足（KpiBoardService 组合既有服务） |
| 原则三：数据完整性、安全与校验 | 服务端权限 | ✅ 满足（大屏仅 ADMIN，@PreAuthorize） |
| 原则四：测试优先与质量门禁 | 测试先于实现 | ✅ 满足（KpiBoardServiceTest + KpiBoardIT + 前端渲染测试） |
| 原则五：简洁、可维护与可观测 | 无 N+1、结构化日志 | ✅ 满足（复用批量聚合；自绘图表避免依赖） |

**结论**: 无门禁违规。

## Project Structure

### Documentation (this feature)

```text
specs/023-kpi-dashboard/
├── plan.md / spec.md / research.md / data-model.md / quickstart.md
├── contracts/（kpi-board 契约）
└── tasks.md
```

### Source Code (repository root)

```text
backend/src/main/java/com/crm/
├── service/KpiBoardService.java               # 新增：组合 dashboard+leaderboard+suggestions+健康度+趋势
├── controller/StatsController.java            # 修改：GET /stats/kpi-board
├── dto/stats/KpiBoardResponse.java            # 新增：聚合 DTO
└── dto/stats/HealthDistribution.java          # 新增：健康度分布（red/yellow/green）

backend/src/test/java/com/crm/
├── service/KpiBoardServiceTest.java           # 新增：聚合正确性/缓存 单元测试
└── integration/KpiBoardIT.java                # 新增：聚合接口 + 权限 集成测试

frontend/src/
├── types/kpiBoard.ts                          # 新增：KpiBoard 类型
├── services/kpiBoardService.ts                # 新增：fetchKpiBoard
├── pages/board/KpiBoardPage.tsx               # 新增：全屏深色大屏页（自动刷新+全屏）
└── App.tsx                                    # 修改：注册大屏路由（数据分析分组）
```

**Structure Decision**: 沿用既有分层。KpiBoardService 注入 DashboardStatsService、TeamLeaderboardService、SuggestionService、CustomerService（健康度分布）组合输出；趋势由 SalesOpportunityMapper 按日聚合。前端大屏页自绘 SVG 图表（折线/条），避免引入 echarts。Redis 缓存 5 分钟（key `stats:kpi-board`）。

## Complexity Tracking

> 无违规，本表留空。
