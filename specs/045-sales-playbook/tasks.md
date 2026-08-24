# Tasks: 销售 Playbook 模块

**Input**: Design documents from `/specs/045-sales-playbook/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2

---

## Phase 1: 数据模型与基础

- [ ] T001 [P] 创建 Flyway 迁移 V55 in `backend/src/main/resources/db/migration/V55__stage_action_template.sql`（stage_action_template 表 + 索引）
- [ ] T002 [P] 创建 Flyway 迁移 V56 in `backend/src/main/resources/db/migration/V56__sales_opportunity_action.sql`（sales_opportunity_action 表 + uk_opp_action 唯一约束）
- [ ] T003 [P] H2 测试 schema 同步（两表 + 索引）in `backend/src/test/resources/schema-h2.sql`
- [ ] T004 [P] ErrorCode 新增 PLAYBOOK_* 错误码 in `backend/src/main/java/com/crm/common/ErrorCode.java`
- [ ] T005 [P] 创建 StageActionTemplate/SalesOpportunityAction 实体 + 2 个 Mapper in `backend/src/main/java/com/crm/entity/` + `backend/src/main/java/com/crm/repository/`

**Checkpoint**: 数据模型就绪

---

## Phase 2: 用户故事 1 - 阶段动作模板配置 (P0)

**Goal**: 管理端模板 CRUD/启停。

**Independent Test**: 配置模板→列表可见→编辑→停用。

### 实现

- [ ] T006 [P] [US1] 创建 ActionTemplateRequest/Response DTO in `backend/src/main/java/com/crm/dto/playbook/`
- [ ] T007 [US1] 创建 StageActionTemplateService（CRUD/阶段校验/启停/审计）in `backend/src/main/java/com/crm/service/StageActionTemplateService.java`
- [ ] T008 [US1] 创建 PlaybookController 模板端点（/stage-actions，仅 ADMIN）in `backend/src/main/java/com/crm/controller/PlaybookController.java`
- [ ] T009 [US1] 创建 StageActionTemplateServiceTest 单元测试 in `backend/src/test/java/com/crm/service/StageActionTemplateServiceTest.java`
- [ ] T010 [US1] 前端类型/服务 in `frontend/src/types/playbook.ts` + `frontend/src/services/playbookService.ts`
- [ ] T011 [US1] 动作模板配置页 in `frontend/src/pages/playbook/StageActionTemplatePage.tsx`（按阶段 CRUD/启停）

**Checkpoint**: US1 可用——模板配置

---

## Phase 3: 用户故事 2 - 销售机会动作引导与完成 (P0)

**Goal**: 详情动作清单 + 勾选完成 + 必做校验。

**Independent Test**: 详情展示清单→勾选→状态持久化→流转提示。

### 实现

- [ ] T012 [P] [US2] 创建 ActionCompleteRequest/ActionViewResponse DTO in `backend/src/main/java/com/crm/dto/playbook/`
- [ ] T013 [US2] 创建 SalesOpportunityActionService（清单查询含完成状态/勾选完成/唯一性校验）in `backend/src/main/java/com/crm/service/SalesOpportunityActionService.java`
- [ ] T014 [US2] PlaybookController 动作端点（/sales-opportunities/{id}/actions，ADMIN+SALES）in `backend/src/main/java/com/crm/controller/PlaybookController.java`
- [ ] T015 [US2] 销售机会详情接口聚合动作清单（含完成状态）in `backend/src/main/java/com/crm/`（SalesOpportunityService/Assembler）
- [ ] T016 [US2] 阶段流转响应增加必做未完成 warning in `backend/src/main/java/com/crm/`（SalesOpportunityService update/close）
- [ ] T017 [US2] 创建 SalesOpportunityActionServiceTest 单元测试 in `backend/src/test/java/com/crm/service/SalesOpportunityActionServiceTest.java`
- [ ] T018 [US2] 创建 SalesPlaybookIT 集成测试 in `backend/src/test/java/com/crm/integration/SalesPlaybookIT.java`
- [ ] T019 [US2] 销售机会详情页接入动作清单与勾选 in `frontend/src/pages/sales-opportunities/`（SalesOpportunityListPage/详情）

**Checkpoint**: US2 可用——动作引导

---

## Phase 4: 收尾与验证

- [ ] T020 App.tsx 点亮"销售Playbook"占位项为路由 in `frontend/src/App.tsx`（/playbook 动作配置页）
- [ ] T021 Backend `mvn verify` 通过（含新增测试、spotless、JaCoCo）
- [ ] T022 单独运行 `mvn test "-Dtest=SalesPlaybookIT"` 通过
- [ ] T023 Frontend typecheck / lint / test / build 通过
- [ ] T024 线上端点验证（模板配置→清单→勾选→重复 409→流转提示）
- [ ] T025 更新契约文档（按实现校正）与 roadmap 045 标记 `[x]`

**Checkpoint**: 模块完整可用

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1**: 无依赖，可并行
- **Phase 2 (US1)**: 依赖 Phase 1
- **Phase 3 (US2)**: 依赖 Phase 1；动作清单聚合需接入 SalesOpportunity 详情
- **Phase 4**: 依赖全部完成

### Parallel Opportunities

- Phase 1 的 T001~T005 全部 [P] 可并行
- US1 模板服务与 US2 动作服务独立实体可并行

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1 数据模型
2. 完成 Phase 2: US1 模板配置
3. **STOP and VALIDATE**: 模板可独立配置
4. 继续 US2 动作引导

### 增量交付

1. Phase 1 → 数据模型就绪
2. US1 模板配置 → 独立验证
3. US2 动作引导（清单/勾选/必做校验）→ 独立验证
4. Phase 4 收尾：菜单点亮/全量验证/文档

## Notes

- v1 不做阶段自定义（阶段枚举保持固定）
- 动作完成记录按机会+模板唯一；模板停用不影响历史
- 必做校验为提示性（前端确认继续，后端不阻断）
- 提交规范：每个逻辑组提交一次（Conventional Commits）
