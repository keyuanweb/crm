# Implementation Plan: 销售 Playbook 模块

**Branch**: `045-sales-playbook` | **Date**: 2026-08-24 | **Spec**: [spec.md](./spec.md)

## Summary

新增阶段动作模板（StageActionTemplate，管理端配置）与销售机会动作完成（SalesOpportunityAction，销售端勾选）。销售机会详情展示当前阶段动作清单，勾选完成持久化；阶段流转时校验必做项（提示可确认继续）。不动阶段枚举。

## Technical Context

**Language/Version**: Java 17 / TypeScript 5（沿用既有技术栈）

**Primary Dependencies**: MyBatis-Plus、Spring Validation、AuditService、Security

**Storage**: MySQL 新增 `stage_action_template` + `sales_opportunity_action` 两张表（Flyway V55/V56）

**Testing**: JUnit 5 + Spring Boot Test（StageActionTemplateServiceTest/SalesOpportunityActionServiceTest 单元、SalesPlaybookIT 集成）

**Target Platform**: Web（管理端动作配置页 + 销售机会详情动作区块）

**Performance Goals**: 动作清单随详情返回 ≤1s（SC-P01）

**Constraints**: 仅活动阶段（INITIAL_CONTACT/NEGOTIATING）可配动作；模板配置仅 ADMIN；动作查看/勾选 ADMIN+SALES；终态阶段无动作引导

**Scale/Scope**: 动作模板 ≤ 数十；完成记录 ≤ 数千

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | Controller→Service→持久层 | ✅ 满足（复用既有分层） |
| 原则三：数据完整性、安全与校验 | DTO 校验、服务端授权 | ✅ 满足（Bean Validation + @PreAuthorize） |
| 原则四：测试优先与质量门禁 | 测试先于实现、合并门禁 | ✅ 满足（单元/集成测试 + 既有门禁） |
| 原则五：简洁、可维护与可观测 | 无 N+1、审计 | ✅ 满足（批量装配 + AuditService） |

**结论**: 无门禁违规。

## Project Structure

```text
specs/045-sales-playbook/
├── spec.md / plan.md / tasks.md / research.md / data-model.md / quickstart.md
├── contracts/playbook.md

backend/src/main/java/com/crm/
├── entity/StageActionTemplate.java + SalesOpportunityAction.java
├── repository/（对应 2 个 Mapper）
├── dto/playbook/（ActionTemplateRequest/Response/ActionCompleteRequest）
├── service/StageActionTemplateService.java（模板 CRUD/启停）
├── service/SalesOpportunityActionService.java（清单查询/勾选完成/必做校验）
├── controller/PlaybookController.java（/stage-actions 模板 + /sales-opportunities/{id}/actions）
├── common/ErrorCode.java（新增 PLAYBOOK_* 错误码）
└── resources/db/migration/V55__stage_action_template.sql + V56__sales_opportunity_action.sql

backend/src/test/java/com/crm/
├── service/StageActionTemplateServiceTest.java + SalesOpportunityActionServiceTest.java
├── integration/SalesPlaybookIT.java

frontend/src/
├── types/playbook.ts + services/playbookService.ts
├── pages/playbook/StageActionTemplatePage.tsx（管理端动作配置）
├── 销售机会详情/列表页接入动作清单与勾选
└── App.tsx（销售管理组点亮"销售Playbook"占位项 → 动作配置路由）
```

**Structure Decision**: 沿用既有前后端目录结构。动作清单在销售机会详情接口聚合返回（复用 assembler 或独立端点）；必做校验在阶段流转 Service 中加提示检查（不阻断，返回 warning 字段或由前端在勾选状态中判断）。

## Complexity Tracking

无违规，本表留空。
