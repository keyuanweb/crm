# Research: 工作流自动化模块

**Branch**: `013-workflow-automation` | **Date**: 2026-08-22

## 1. 规则模型与条件/动作 JSON

**Decision**: `workflow_rule` 表：id、name、event_type（LEAD_CREATED/OPPORTUNITY_STAGE_CHANGED/FOLLOW_UP_CREATED/PAYMENT_RECORDED）、condition_json（`{"field":"stage","value":"NEGOTIATING"}`，v1 单一等值）、action_type（CREATE_TASK/ASSIGN/NOTIFY）、action_json（CREATE_TASK: `{"titleTemplate":"跟进{name}","dueDays":3}`；ASSIGN: `{"targetUserId":5}`；NOTIFY: `{"message":"线索已创建"}`）、enabled、逻辑删除/乐观锁/时间戳。条件/动作用 Jackson 序列化存储（String 列）。

**Rationale**: 结构化 JSON 兼顾扩展性与简单性；v1 单一条件（spec 假设）。

## 2. 触发与执行（WorkflowEngine）

**Decision**: `WorkflowEventPublisher.fire(eventType, entityType, entityId, context)` 由业务 Service 调用（线索创建后、商机阶段变更后、跟进创建后、回款登记后）。WorkflowEngine：查询该事件全部启用规则 → 逐条匹配条件（context 字段等值）→ 执行动作：
- CREATE_TASK：调 TaskService.create（title 模板替换、dueAt=now+dueDays）；
- ASSIGN：线索→Lead.ownerId、客户→Customer.ownerId 更新为目标用户（目标不存在则记失败日志）；
- NOTIFY：插入一条通知记录（简化：写入 workflow 通知表或复用 audit）。
执行结果写入 workflow_execution_log（规则、事件、实体、匹配/执行结果、错误）。**整个执行包 try/catch，失败仅记日志不影响主流程**。

**Rationale**: 同步执行（spec 假设）；失败隔离保障主流程；日志可观测。

## 3. 通知记录

**Decision**: NOTIFY 动作写入 `workflow_notification`（简化实体：接收人、消息、已读状态）或直接复用执行日志呈现。v1 用独立小表 `workflow_notification`（可后续接通知中心）。

**Rationale**: spec 假设站内记录；独立表便于前端展示"通知"。

## 4. 避免递归触发

**Decision**: 动作执行不产生触发事件（ASSIGN 直接改字段不调业务 create 流程；CREATE_TASK 直连 TaskService 的纯插入路径），保证无递归。

**Rationale**: 防规则级联爆炸；实现简单。

## 5. 契约与权限

**Decision**: 契约写入 `contracts/workflow.md`。规则 CRUD/日志/通知查询：仅 ADMIN（类级 @PreAuthorize）。前端规则管理页（触发事件/条件/动作表单）+ 执行日志页。

**Rationale**: 与既有权限模式一致；服务端强制授权（章程原则三）。
