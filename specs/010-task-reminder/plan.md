# Implementation Plan: 任务与提醒模块

**Branch**: `010-task-reminder` | **Date**: 2026-08-22 | **Spec**: [spec.md](./spec.md)

## Summary

新增任务实体（TaskItem）：个人待办任务 CRUD、完成/重开、逾期/今日到期标识与提醒汇总、按月日历数据；添加跟进记录时可勾选自动创建跟进任务（截止=nextFollowUpAt）。前端新增任务列表页（ProTable + 逾期/今日 Tag）与日历视图页（antd Calendar）。

## Technical Context

**Language/Version**: Java 17 / TypeScript 5（沿用既有技术栈）

**Primary Dependencies**: MyBatis-Plus、Spring Validation、AuditService

**Storage**: MySQL 新增 `task_item` 表（Flyway V22）

**Testing**: JUnit 5 + Spring Boot Test（TaskServiceTest 单元、TaskIT 集成）

**Target Platform**: Web（任务列表页 + 日历视图页）

**Project Type**: 现有 Web 应用新增模块

**Performance Goals**: 任务列表/创建 ≤1s（SC-T01）

**Constraints**: 任务归属当前用户（数据隔离）；逾期=截止<现在且 TODO；跟进自动创建任务（勾选时）；优先级枚举；逻辑删除

**Scale/Scope**: 每用户任务数量级 ≤ 数百

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | Controller→Service→持久层 | ✅ 满足（复用既有分层） |
| 原则三：数据完整性、安全与校验 | DTO 校验、服务端授权、数据隔离 | ✅ 满足（Bean Validation + 按 ownerId 过滤） |
| 原则四：测试优先与质量门禁 | 测试先于实现、合并门禁 | ✅ 满足（单元/集成测试 + 既有门禁） |
| 原则五：简洁、可维护与可观测 | 无 N+1、审计 | ✅ 满足（按用户查询 + AuditService） |

**结论**: 无门禁违规。

## Project Structure

```text
specs/010-task-reminder/
├── spec.md / plan.md / tasks.md / research.md / data-model.md / quickstart.md
├── contracts/tasks.md

backend/src/main/java/com/crm/
├── entity/TaskItem.java + repository/TaskItemMapper.java
├── dto/task/（TaskRequest/TaskResponse/CalendarDay）
├── service/TaskService.java（CRUD/完成重开/逾期标识/汇总/日历数据）
├── controller/TaskController.java
├── common/ErrorCode.java（新增 TASK_NOT_FOUND）
└── resources/db/migration/V22__task_item.sql

backend/src/test/java/com/crm/
├── service/TaskServiceTest.java
├── integration/TaskIT.java

frontend/src/
├── types/task.ts + services/taskService.ts
├── pages/tasks/TaskListPage.tsx（ProTable + 提醒汇总卡片）
├── pages/tasks/TaskCalendarPage.tsx（antd Calendar + 日任务弹层）
└── App.tsx（任务菜单 + 路由）
```

**Structure Decision**: 沿用既有前后端目录结构（Option 2 Web 应用）。任务为轻量个人待办，单 Service/Controller；日历数据由同一 Service 按月返回（date → tasks）。

## Complexity Tracking

无违规，本表留空。
