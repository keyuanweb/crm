# Tasks: 工作流自动化模块

**Input**: Design documents from `/specs/013-workflow-automation/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2/US3

---

## Phase 1: 数据模型与基础

- [x] T001 [P] 创建 Flyway 迁移 V27 in `backend/src/main/resources/db/migration/V27__workflow_rule.sql`（workflow_rule 表 + event/enabled 索引）
- [x] T002 [P] 创建 Flyway 迁移 V28 in `backend/src/main/resources/db/migration/V28__workflow_execution_log.sql`（workflow_execution_log 表）
- [x] T003 [P] 创建 Flyway 迁移 V29 in `backend/src/main/resources/db/migration/V29__workflow_notification.sql`（workflow_notification 表）
- [x] T004 [P] 创建 WorkflowRule 实体/Mapper in `backend/src/main/java/com/crm/entity/WorkflowRule.java` + `backend/src/main/java/com/crm/repository/WorkflowRuleMapper.java`
- [x] T005 [P] 创建 WorkflowExecutionLog 实体/Mapper in `backend/src/main/java/com/crm/entity/WorkflowExecutionLog.java` + `backend/src/main/java/com/crm/repository/WorkflowExecutionLogMapper.java`
- [x] T006 [P] 创建 WorkflowNotification 实体/Mapper in `backend/src/main/java/com/crm/entity/WorkflowNotification.java` + `backend/src/main/java/com/crm/repository/WorkflowNotificationMapper.java`
- [x] T007 [P] H2 测试 schema 同步三表 in `backend/src/test/resources/schema-h2.sql`
- [x] T008 [P] ErrorCode 新增 WORKFLOW_RULE_NOT_FOUND/WORKFLOW_INVALID_ACTION in `backend/src/main/java/com/crm/common/ErrorCode.java`

**Checkpoint**: 数据模型就绪

---

## Phase 2: 用户故事 1 - 自动化规则管理 (P0) 🎯 MVP

**Goal**: 规则 CRUD + 启停。

**Independent Test**: 建规则→列表→编辑→停用。

### 实现

- [x] T009 [P] [US1] 创建 WorkflowRuleRequest/WorkflowRuleResponse/ExecutionLogResponse DTO in `backend/src/main/java/com/crm/dto/workflow/`（condition/action 用 Map 或嵌套对象）
- [x] T010 [US1] 创建 WorkflowRuleService in `backend/src/main/java/com/crm/service/WorkflowRuleService.java`（CRUD/启停/动作 JSON 校验）
- [x] T011 [US1] 创建 WorkflowController in `backend/src/main/java/com/crm/controller/WorkflowController.java`（GET/POST/PUT/DELETE /workflows/rules、toggle；仅 ADMIN）
- [x] T012 [US1] 创建 WorkflowRuleServiceTest 单元测试 in `backend/src/test/java/com/crm/service/WorkflowRuleServiceTest.java`（CRUD/动作校验）
- [x] T013 [US1] 创建 WorkflowIT 集成测试（规则部分）in `backend/src/test/java/com/crm/integration/WorkflowIT.java`
- [x] T014 [US1] 前端类型/服务 in `frontend/src/types/workflow.ts` + `frontend/src/services/workflowService.ts`
- [x] T015 [US1] 规则管理页 in `frontend/src/pages/workflows/WorkflowRuleListPage.tsx`（ProTable + 触发事件/条件/动作表单 + 启停）

**Checkpoint**: US1 可用——规则管理

---

## Phase 3: 用户故事 2 - 触发与执行 (P0)

**Goal**: WorkflowEngine 匹配启用规则并执行动作（CREATE_TASK/ASSIGN/NOTIFY），业务事件接入。

**Independent Test**: 建"线索创建→自动分配"规则→建线索→ownerId 自动设置。

### 实现

- [x] T016 [US2] 创建 WorkflowEventPublisher in `backend/src/main/java/com/crm/service/WorkflowEventPublisher.java`（fire(eventType, entityType, entityId, context)）
- [x] T017 [US2] 创建 WorkflowEngine in `backend/src/main/java/com/crm/service/WorkflowEngine.java`（查启用规则→匹配条件→执行动作→记日志；try/catch 失败隔离；ASSIGN 目标不存在记失败）
- [x] T018 [US2] 创建 WorkflowNotificationService in `backend/src/main/java/com/crm/service/WorkflowNotificationService.java`（NOTIFY 动作写通知/列表/标记已读）
- [x] T019 [US2] LeadService.create 接入事件（LEAD_CREATED）in `backend/src/main/java/com/crm/service/LeadService.java`
- [x] T020 [US2] SalesOpportunityService 阶段变更接入事件（OPPORTUNITY_STAGE_CHANGED）in `backend/src/main/java/com/crm/service/SalesOpportunityService.java`
- [x] T021 [US2] FollowUpService.create 接入事件（FOLLOW_UP_CREATED）in `backend/src/main/java/com/crm/service/FollowUpService.java`
- [x] T022 [US2] PaymentService.recordPayment 接入事件（PAYMENT_RECORDED）in `backend/src/main/java/com/crm/service/PaymentService.java`
- [x] T023 [US2] WorkflowController 增加 GET /workflows/logs、GET /workflows/notifications、POST /workflows/notifications/{id}/read in `backend/src/main/java/com/crm/controller/WorkflowController.java`
- [x] T024 [US2] 创建 WorkflowEngineTest 单元测试 in `backend/src/test/java/com/crm/service/WorkflowEngineTest.java`（匹配/三动作/失败隔离/停用不触发）
- [x] T025 [US2] WorkflowIT 增加触发执行用例（线索自动分配/阶段建任务/回款建任务/日志）in `backend/src/test/java/com/crm/integration/WorkflowIT.java`

**Checkpoint**: US2 可用——触发执行

---

## Phase 4: 用户故事 3 - 执行日志 (P1)

**Goal**: 执行日志完整记录与查询。

**Independent Test**: 触发规则→日志可见→结果正确。

### 实现

- [x] T026 [US3] WorkflowEngine 完善日志记录（matched/actionResult/success/errorMessage）in `backend/src/main/java/com/crm/service/WorkflowEngine.java`
- [x] T027 [US3] WorkflowRuleServiceTest/WorkflowEngineTest 增加日志断言 in `backend/src/test/java/com/crm/service/WorkflowEngineTest.java`
- [x] T028 [US3] 前端执行日志页 in `frontend/src/pages/workflows/WorkflowLogListPage.tsx`（ProTable + 筛选）

**Checkpoint**: US3 可用——执行日志

---

## Phase 5: 收尾与验证

- [x] T029 前端路由与菜单 in `frontend/src/App.tsx`（工作流菜单 + 规则/日志路由，仅 ADMIN）
- [x] T030 Backend `mvn verify` 通过（含新增测试、spotless、JaCoCo）
- [x] T031 单独运行 `mvn test "-Dtest=WorkflowIT"` 通过
- [x] T032 Frontend typecheck / lint / test / build 通过
- [x] T033 线上端点验证（规则 CRUD→启停→线索自动分配→阶段建任务→回款建任务→日志→通知）
- [x] T034 更新契约文档（按实现校正）与 roadmap 013 标记 `[x]`

**Checkpoint**: 模块完整可用
