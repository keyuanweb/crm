# Implementation Plan: 通用多级审批流

**Branch**: `033-approval-flow` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

## Summary

新增通用审批引擎：`approval_flow`（定义：业务类型 + 节点序列 + 条件分支）+ `approval_instance`（实例）+ `approval_task`（任务）+ `approval_log`（日志）；`ApprovalFlowService`（定义 CRUD）+ `ApprovalEngineService`（发起/通过/驳回/转交/重提，多级流转 + 条件分支匹配 + 026 通知）；合同/报价单提交审批接入引擎（兼容未配置走原逻辑）。

## Technical Context

**Language/Version**: Java 17（后端）+ TypeScript/React 18（前端）

**Primary Dependencies**: MyBatis-Plus、026 NotificationService、017 WorkflowService（复用触发）

**Storage**: 4 表 + Flyway V49；节点/条件 JSON 存 flow 表

**Testing**: JUnit 5 + Mockito（引擎状态机：多级/分支/驳回重提）、集成（ApprovalIT：发起→通过→驳回→重提全流程）、前端（审批配置页 + 我的审批列表渲染）

**Target Platform**: Web

**Project Type**: Web 应用

**Performance Goals**: 审批操作 ≤ 100ms；通知即时（026）

**Constraints**: 状态机校验（并发单任务）；条件分支基于金额字段；审批人类型：角色/指定人/发起人上级

**Scale/Scope**: 4 表 + 1 迁移 + 2 Service + 2 Controller + 合同/报价接入 + 前端配置/审批中心

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则一：契约优先的 API 设计 | 端点契约先于实现 | ✅ 满足（contracts/approval-flow.md） |
| 原则二：分层架构与关注点分离 | Controller→Service→Repository | ✅ 满足（Flow 定义与 Engine 分离） |
| 原则三：数据完整性、安全与校验 | 状态机校验 | ✅ 满足（任务幂等/并发校验 + 审批人权限） |
| 原则四：测试优先与质量门禁 | 测试先于实现 | ✅ 满足（ApprovalEngineTest/ApprovalIT + 前端测试） |
| 原则五：简洁、可维护与可观测 | 避免过度设计 | ✅ 满足（自研状态机，不引流程引擎） |

**结论**: 无门禁违规。

## Project Structure

### Source Code

```text
backend/src/main/java/com/crm/
├── entity/ApprovalFlow.java / ApprovalInstance.java / ApprovalTask.java / ApprovalLog.java
├── repository/4 Mapper
├── dto/approval/FlowRequest.java / FlowResponse.java / TaskActionRequest.java / InstanceResponse.java
├── service/ApprovalFlowService.java     # 流程定义 CRUD + 条件分支解析 + 节点匹配
├── service/ApprovalEngineService.java   # 发起/通过/驳回/转交/重提 + 状态机 + 通知
├── controller/ApprovalController.java   # /api/v1/approval-flows（配置）+ /api/v1/approvals（我的待办/历史/操作）
└── service/ContractService.java 等      # 修改：提交审批接引擎

backend/src/main/resources/db/migration/V49__approval_flow.sql
backend/src/test/java/com/crm/
├── service/ApprovalEngineServiceTest.java
└── integration/ApprovalIT.java

frontend/src/
├── types/approval.ts / services/approvalService.ts
├── pages/approval/ApprovalFlowPage.tsx    # 审批流配置（系统管理）
├── pages/approval/ApprovalCenterPage.tsx  # 我的审批（待办/已办）
└── App.tsx                                 # 注册路由
```

**Structure Decision**: 自研状态机：instance(status: PENDING/APPROVED/REJECTED/CANCELED) + task(status: PENDING/APPROVED/REJECTED/TRANSFERRED)。发起时按 flow 条件匹配节点序列生成多任务；每节点通过推进下一节点；驳回回退发起人（instance REJECTED，可重提新建）。通知复用 026（审批人收到待办）。业务接入：合同/报价提交时若存在启用 flow 则走引擎，否则原逻辑。

## Complexity Tracking

> 无违规，本表留空。
