# Implementation Plan: AI 智能助手（规则型智能建议）

**Branch**: `022-ai-assistant` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/022-ai-assistant/spec.md`

## Summary

新增规则型智能建议服务 `SuggestionService`：聚合四类待办（客户流失预警、商机停滞预警、待跟进客户、高分线索待处理），按优先级排序生成建议列表；复用 018 `CustomerService.atRiskCustomers`/`Customer360Service`、`DashboardStatsService` 停滞预警、019 `LeadScoreService` 高分线索；忽略记录存 Redis。前端新增建议页 + 首页摘要卡片。

## Technical Context

**Language/Version**: Java 17（后端）+ TypeScript/React 18（前端）

**Primary Dependencies**: Spring Boot 3.2、MyBatis-Plus（CustomerMapper/FollowUpMapper/SalesOpportunityMapper/LeadMapper）、Redis（忽略记录）、antd 5

**Storage**: 忽略记录存 Redis（`ai:ignore:<userId>:<type>:<entityId>`，TTL 90 天）；无新表

**Testing**: JUnit 5 + Mockito（单元）、Spring Boot Test + MockMvc（集成）、Vitest + RTL（前端）

**Target Platform**: Web

**Project Type**: Web 应用（Spring Boot 后端 + React 前端）

**Performance Goals**: 建议列表 ≤ 1s（复用聚合服务 + 上限 20 条）

**Constraints**: 规则引擎（非 LLM）；复用 018/019/停滞预警数据；SALES 仅本人数据；忽略用 Redis

**Scale/Scope**: 1 个建议服务 + 1 个控制器端点 + 忽略接口 + 前端建议页 + 首页摘要

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则一：契约优先的 API 设计 | 端点契约先于实现 | ✅ 满足（contracts/smart-suggestions.md 定义建议列表与忽略契约） |
| 原则二：分层架构与关注点分离 | Controller→Service→Repository | ✅ 满足（SuggestionService 聚合规则，Controller 薄） |
| 原则三：数据完整性、安全与校验 | 服务端权限 | ✅ 满足（建议按 012 数据权限过滤；忽略按用户隔离） |
| 原则四：测试优先与质量门禁 | 测试先于实现 | ✅ 满足（SuggestionServiceTest + SmartSuggestionIT + 前端渲染测试） |
| 原则五：简洁、可维护与可观测 | 无 N+1、结构化日志 | ✅ 满足（复用批量装配；建议生成记 DEBUG 日志） |

**结论**: 无门禁违规。

## Project Structure

### Documentation (this feature)

```text
specs/022-ai-assistant/
├── plan.md / spec.md / research.md / data-model.md / quickstart.md
├── contracts/（smart-suggestions 契约）
└── tasks.md
```

### Source Code (repository root)

```text
backend/src/main/java/com/crm/
├── service/SuggestionService.java              # 新增：四类规则聚合 + 去重 + 排序 + 忽略过滤
├── controller/SuggestionController.java        # 新增：GET /api/v1/suggestions、POST /suggestions/{type}/{id}/ignore
├── dto/suggestion/SmartSuggestion.java         # 新增：type/title/reason/priority/entityType/entityId/action
├── dto/suggestion/SuggestionSummary.java       # 新增：各类型计数（首页摘要）

backend/src/test/java/com/crm/
├── service/SuggestionServiceTest.java          # 新增：规则/去重/排序/忽略 单元测试
└── integration/SmartSuggestionIT.java          # 新增：建议列表 + 忽略 集成测试

frontend/src/
├── types/suggestion.ts                         # 新增：SmartSuggestion 类型
├── services/suggestionService.ts               # 新增：fetchSuggestions/ignoreSuggestion/fetchSummary
├── pages/assistant/SuggestionCenterPage.tsx    # 新增：智能建议列表页
├── pages/stats/DashboardPage.tsx               # 修改：建议摘要卡片（US3）
└── App.tsx                                     # 修改：注册建议路由（数据分析分组）
```

**Structure Decision**: 沿用既有分层。建议实时计算（复用现有聚合服务），忽略过滤在 Redis 集合中判断；建议上限 20 条。前端建议页用 List 组件展示类型/原因/优先级，首页摘要卡用 Statistic。

## Complexity Tracking

> 无违规，本表留空。
