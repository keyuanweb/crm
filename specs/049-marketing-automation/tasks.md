# Tasks: 营销自动化模块

**Input**: Design documents from `/specs/049-marketing-automation/`

**Prerequisites**: plan.md (required), spec.md (required), research.md

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2

---

## Phase 1: 营销事件（US1）

**Goal**: LEAD_SCORE_THRESHOLD / TAG_CHANGED 事件发布与匹配。

**Independent Test**: 评分达阈值/标签变更 → 规则触发。

### 实现

- [ ] T001 [P] [US1] WorkflowEngine 增加营销事件常量与数值条件匹配（score >= 阈值）in `backend/src/main/java/com/crm/service/WorkflowEngine.java`
- [ ] T002 [P] [US1] WorkflowEventPublisher 增加 leadScoreThreshold/tagChanged 发布方法 in `backend/src/main/java/com/crm/service/WorkflowEventPublisher.java`
- [ ] T003 [US1] LeadScoringService 评分重算后发布 LEAD_SCORE_THRESHOLD in `backend/src/main/java/com/crm/service/LeadScoringService.java`
- [ ] T004 [US1] 标签服务打标后发布 TAG_CHANGED in `backend/src/main/java/com/crm/service/`（TagService/CustomerTagService）

**Checkpoint**: US1 可用——营销事件触发

---

## Phase 2: 营销动作（US2）

**Goal**: SEND_EMAIL / ADD_TAG 动作执行。

**Independent Test**: 规则触发 → 邮件记录/标签添加。

### 实现

- [ ] T005 [P] [US2] WorkflowEngine execute 增加 SEND_EMAIL（发模板邮件给线索邮箱）in `backend/src/main/java/com/crm/service/WorkflowEngine.java`
- [ ] T006 [P] [US2] WorkflowEngine execute 增加 ADD_TAG（为线索添加标签）in `backend/src/main/java/com/crm/service/WorkflowEngine.java`
- [ ] T007 [US2] WorkflowRuleService 事件/动作类型校验扩展（营销选项）in `backend/src/main/java/com/crm/service/WorkflowRuleService.java`
- [ ] T008 [US2] 创建 MarketingAutomationServiceTest 单元测试 in `backend/src/test/java/com/crm/service/MarketingAutomationServiceTest.java`
- [ ] T009 [US2] 创建 MarketingAutomationIT 集成测试 in `backend/src/test/java/com/crm/integration/MarketingAutomationIT.java`
- [ ] T010 [US2] 前端规则配置页事件/动作下拉扩展营销选项 in `frontend/src/pages/workflows/WorkflowRulePage.tsx`

**Checkpoint**: US2 可用——营销动作执行

---

## Phase 3: 收尾与验证

- [ ] T011 Backend `mvn verify` 通过（含新增测试、spotless、JaCoCo）
- [ ] T012 单独运行 `mvn test "-Dtest=MarketingAutomationIT"` 通过
- [ ] T013 Frontend typecheck / lint / build 通过
- [ ] T014 线上端点验证（评分规则触发→邮件/任务；标签规则→加标签）
- [ ] T015 更新 roadmap 049 标记 `[x]`

**Checkpoint**: 模块完整可用

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1**: 依赖 013/019/031 既有服务
- **Phase 2**: 依赖 Phase 1（动作在引擎中执行）
- **Phase 3**: 依赖全部完成

## Notes

- 复用 013 WorkflowRule/日志，无新表
- SEND_EMAIL 为单封模板邮件（批量走 030）
- 提交规范：Conventional Commits
