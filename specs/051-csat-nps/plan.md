# Implementation Plan: 工单满意度调查（CSAT/NPS）模块

**Branch**: `051-csat-nps` | **Date**: 2026-08-25 | **Spec**: [spec.md](./spec.md)

## Summary

新增工单满意度调查：`ticket_survey` 表（工单唯一、评分 1-5、评语、提交人/时间）；工单 CLOSED 后可提交评分（重复 409、状态校验）；CSAT 均值 + NPS 分布统计（支持时间筛选）。

## Technical Context

**Language/Version**: Java 17 / TypeScript 5（沿用）

**Primary Dependencies**: MyBatis-Plus、Spring Validation、TicketService（015）

**Storage**: MySQL 新增 `ticket_survey` 表（Flyway V59）

**Testing**: JUnit 5（TicketSurveyServiceTest 单元、TicketSurveyIT 集成）

**Target Platform**: Web（工单详情评分区块 + 满意度统计页）

**Project Type**: 既有模块增强（015）

**Performance Goals**: 评分提交与统计查询 ≤1s

**Constraints**: 仅 CLOSED 工单可评；一张工单一次评分；评分 1-5；评语 ≤500 字

**Scale/Scope**: 评分记录 ≤ 数千

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | Controller→Service→持久层 | ✅ 满足 |
| 原则三：数据完整性、安全与校验 | DTO 校验、服务端授权 | ✅ 满足 |
| 原则四：测试优先与质量门禁 | 测试先于实现 | ✅ 满足 |
| 原则五：简洁、可维护与可观测 | 无 N+1、审计 | ✅ 满足 |

**结论**: 无门禁违规。

## Project Structure

```text
specs/051-csat-nps/
├── spec.md / plan.md / tasks.md / research.md / data-model.md / quickstart.md
├── contracts/survey.md

backend/src/main/java/com/crm/
├── entity/TicketSurvey.java + repository/TicketSurveyMapper.java
├── dto/survey/（SurveyRequest/SurveyResponse/SurveyStatsResponse）
├── service/TicketSurveyService.java（评分/统计）
├── controller/TicketSurveyController.java（/tickets/{id}/survey + /surveys/stats）
├── common/ErrorCode.java（新增 SURVEY_* 错误码）
└── resources/db/migration/V59__ticket_survey.sql

backend/src/test/java/com/crm/
├── service/TicketSurveyServiceTest.java
├── integration/TicketSurveyIT.java

frontend/src/
├── services/ticketSurveyService.ts + types/survey.ts
├── 工单详情评分区块
├── pages/surveys/SatisfactionStatsPage.tsx（CSAT/NPS 统计）
└── App.tsx（服务协作组点亮"满意度调查"占位项 → 路由）
```

**Structure Decision**: 独立 TicketSurveyService（评分+统计）；工单详情复用既有详情页加评分区块。

## Complexity Tracking

无违规，本表留空。
