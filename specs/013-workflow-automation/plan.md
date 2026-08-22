# Implementation Plan: 工作流自动化模块

**Branch**: `013-workflow-automation` | **Date**: 2026-08-22 | **Spec**: [spec.md](./spec.md)

## Summary

新增工作流规则（WorkflowRule）与执行日志（WorkflowExecutionLog）实体：规则为"触发事件 + 条件 + 动作"结构化配置（CRUD/启停，仅 ADMIN）；四类业务事件（线索创建/商机阶段变更/跟进创建/回款登记）发生时由 WorkflowEngine 匹配启用规则并执行动作（CREATE_TASK 创建任务 / ASSIGN 自动分配 / NOTIFY 站内通知记录）；每次执行记录日志（失败不影响主流程）。前端新增规则管理页与执行日志页。

## Technical Context

**Language/Version**: Java 17 / TypeScript 5（沿用既有技术栈）

**Primary Dependencies**: MyBatis-Plus、Jackson（条件/动作 JSON）、AuditService、TaskService（CREATE_TASK）、LeadService/CustomerPoolService（ASSIGN）

**Storage**: MySQL 新增 `workflow_rule`、`workflow_execution_log` 表（Flyway V27~V28）

**Testing**: JUnit 5 + Spring Boot Test（WorkflowEngineTest/WorkflowRuleServiceTest 单元、WorkflowIT 集成）

**Target Platform**: Web（规则管理页 + 执行日志页，仅 ADMIN）

**Project Type**: 现有 Web 应用新增模块

**Performance Goals**: 规则保存/触发匹配 ≤1s（SC-W01/W02）

**Constraints**: 事件与动作类型固定枚举；条件单一等值匹配；仅启用规则参与；执行失败不影响主流程；避免递归触发；规则管理仅 ADMIN

**Scale/Scope**: 规则 ≤ 数十；执行日志 ≤ 数千

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | Controller→Service→持久层 | ✅ 满足（复用既有分层，WorkflowEngine 独立） |
| 原则三：数据完整性、安全与校验 | DTO 校验、服务端授权 | ✅ 满足（Bean Validation + @PreAuthorize 仅 ADMIN） |
| 原则四：测试优先与质量门禁 | 测试先于实现、合并门禁 | ✅ 满足（单元/集成测试 + 既有门禁） |
| 原则五：简洁、可维护与可观测 | 无 N+1、执行日志 | ✅ 满足（批量查询 + 执行日志） |

**结论**: 无门禁违规。

## Project Structure

```text
specs/013-workflow-automation/
├── spec.md / plan.md / tasks.md / research.md / data-model.md / quickstart.md
├── contracts/workflow.md

backend/src/main/java/com/crm/
├── entity/WorkflowRule.java + repository/WorkflowRuleMapper.java
├── entity/WorkflowExecutionLog.java + repository/WorkflowExecutionLogMapper.java
├── dto/workflow/（WorkflowRuleRequest/WorkflowRuleResponse/ExecutionLogResponse）
├── service/WorkflowRuleService.java（规则 CRUD/启停/查询启用规则）
├── service/WorkflowEngine.java（事件触发→匹配→执行动作→记日志，失败吞异常）
├── service/WorkflowEventPublisher.java（供业务 Service 调用触发）
├── controller/WorkflowController.java（规则管理 + 执行日志，仅 ADMIN）
├── common/ErrorCode.java（新增 WORKFLOW_* 错误码）
└── resources/db/migration/V27__workflow_rule.sql + V28__workflow_execution_log.sql

backend/src/test/java/com/crm/
├── service/WorkflowEngineTest.java + WorkflowRuleServiceTest.java
├── integration/WorkflowIT.java

frontend/src/
├── types/workflow.ts + services/workflowService.ts
├── pages/workflows/WorkflowRuleListPage.tsx（规则管理，仅 ADMIN）
├── pages/workflows/WorkflowLogListPage.tsx（执行日志，仅 ADMIN）
└── App.tsx（工作流菜单 + 路由，仅 ADMIN）
```

**Structure Decision**: 沿用既有前后端目录结构（Option 2 Web 应用）。规则匹配与执行独立 `WorkflowEngine`；业务 Service 通过 `WorkflowEventPublisher` 触发（解耦）。

## Complexity Tracking

无违规，本表留空。
