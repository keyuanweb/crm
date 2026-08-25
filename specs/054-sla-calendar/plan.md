# Implementation Plan: SLA 工作时间与节假日日历模块

**Branch**: `054-sla-calendar` | **Date**: 2026-08-25 | **Spec**: [spec.md](./spec.md)

## Summary

引入工作日历：`sla_calendar_config` 表（工作时间段 JSON、工作周、节假日 JSON、启用）。TicketService.applySla 改为基于日历计算到期时间（跳过非工作时间与节假日）；无配置回退 24h。

## Technical Context

**Language/Version**: Java 17 / TypeScript 5（沿用）

**Primary Dependencies**: TicketService（015）、SlaPolicy、Jackson

**Storage**: MySQL 新增 `sla_calendar_config` 表（V61）

**Testing**: JUnit 5（SlaCalendarServiceTest 单元，覆盖跨周末/跨夜/节假日；SlaCalendarIT 集成）

**Target Platform**: Web（SLA 日历配置页 + 工单详情 SLA 展示不变）

**Project Type**: 既有模块增强（015）

**Performance Goals**: 计算 ≤10ms（内存解析配置）

**Constraints**: 仅新工单生效；无配置回退 24h；配置仅 ADMIN

**Scale/Scope**: 单条全局日历配置

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | 独立日历服务 + TicketService 调用 | ✅ 满足 |
| 原则三：数据完整性、安全与校验 | DTO 校验、仅 ADMIN | ✅ 满足 |
| 原则四：测试优先与质量门禁 | 单元测试覆盖边界 | ✅ 满足 |
| 原则五：简洁、可维护与可观测 | 计算纯函数、JSON 配置 | ✅ 满足 |

**结论**: 无门禁违规。

## Project Structure

```text
specs/054-sla-calendar/
├── spec.md / plan.md / tasks.md / research.md / data-model.md / quickstart.md
├── contracts/sla-calendar.md

backend/src/main/java/com/crm/
├── entity/SlaCalendarConfig.java + repository/SlaCalendarConfigMapper.java
├── dto/sla/（SlaCalendarConfigRequest/Response）
├── service/SlaCalendarService.java（配置 CRUD + 日历计算 advanceWorkingTime）
├── service/TicketService.java（applySla 改用日历）
├── controller/SlaCalendarController.java（/sla-calendar，仅 ADMIN）
├── common/ErrorCode.java（新增 SLA_CALENDAR_* 错误码）
└── resources/db/migration/V61__sla_calendar_config.sql

backend/src/test/java/com/crm/
├── service/SlaCalendarServiceTest.java（跨周末/跨夜/节假日）
├── integration/SlaCalendarIT.java

frontend/src/
├── services/slaCalendarService.ts + types/slaCalendar.ts
├── pages/sla/SlaCalendarPage.tsx（工作时间/节假日配置）
└── App.tsx（流程与配置组点亮"SLA日历"占位项 → 路由）
```

**Structure Decision**: SlaCalendarService 提供 `advanceWorkingTime(from, hours)` 纯函数；TicketService.applySla 调之（无配置时直接 plusHours 回退）。

## Complexity Tracking

无违规，本表留空。
