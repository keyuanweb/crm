# Implementation Plan: 营销自动化模块

**Branch**: `049-marketing-automation` | **Date**: 2026-08-25 | **Spec**: [spec.md](./spec.md)

## Summary

扩展 013 工作流引擎支持营销场景：新增事件 LEAD_SCORE_THRESHOLD（评分达阈值）、TAG_CHANGED（标签变更）与动作 SEND_EMAIL（发模板邮件）、ADD_TAG（加标签）。在评分重算与标签服务中发布事件；动作执行失败隔离。

## Technical Context

**Language/Version**: Java 17 / TypeScript 5（沿用）

**Primary Dependencies**: WorkflowEngine（013）、LeadScoringService（019）、EmailTemplateService（030）、TagService（031）

**Storage**: 无新表（复用 workflow_rule / workflow_execution_log）

**Testing**: JUnit 5（MarketingAutomationServiceTest 单元、MarketingAutomationIT 集成）

**Target Platform**: Web（规则配置沿用 013 页面 + 事件类型/动作类型选项扩展）

**Project Type**: 既有模块扩展（013/019/030/031 交叉）

**Performance Goals**: 事件触发到执行 ≤1s

**Constraints**: 规则配置仅 ADMIN（沿用）；执行失败隔离不阻断主流程

**Scale/Scope**: 营销规则 ≤ 数十

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | 事件发布点仅调用门面 | ✅ 满足（WorkflowEventPublisher 模式） |
| 原则三：数据完整性、安全与校验 | 服务端授权、参数校验 | ✅ 满足 |
| 原则四：测试优先与质量门禁 | 测试先于实现 | ✅ 满足 |
| 原则五：简洁、可维护与可观测 | 复用既有机制、执行日志 | ✅ 满足 |

**结论**: 无门禁违规。

## Project Structure

```text
specs/049-marketing-automation/
├── spec.md / plan.md / tasks.md / research.md / quickstart.md
└── checklists/requirements.md

backend/src/main/java/com/crm/
├── service/WorkflowEngine.java（+SEND_EMAIL/ADD_TAG 动作 + 事件常量）
├── service/WorkflowEventPublisher.java（+leadScoreThreshold/tagChanged 发布）
├── service/LeadScoringService.java（评分重算后发布 LEAD_SCORE_THRESHOLD）
├── service/CustomerTagService.java 或 TagService（打标后发布 TAG_CHANGED）
└── service/WorkflowRuleService.java（事件/动作类型校验扩展）

backend/src/test/java/com/crm/
├── service/MarketingAutomationServiceTest.java
├── integration/MarketingAutomationIT.java

frontend/src/pages/workflows/WorkflowRulePage.tsx（事件/动作类型下拉扩展营销选项）
```

**Structure Decision**: 全部扩展在既有 013 链上完成（无新表/新 Controller），复用规则配置页；事件发布点两个。

## Complexity Tracking

无违规，本表留空。
