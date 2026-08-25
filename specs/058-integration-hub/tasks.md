# Tasks: 集成中心模块

**Input**: Design documents from `/specs/058-integration-hub/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2

---

## Phase 1: 数据模型与基础

- [ ] T001 [P] 创建 Flyway 迁移 V66 in `backend/src/main/resources/db/migration/V66__integration_channel.sql`
- [ ] T002 [P] H2 测试 schema 同步 in `backend/src/test/resources/schema-h2.sql`
- [ ] T003 [P] 创建 IntegrationChannel 实体 + Mapper in `backend/src/main/java/com/crm/entity/` + `backend/src/main/java/com/crm/repository/`
- [ ] T004 [P] ErrorCode 新增 INTEGRATION_* 错误码 in `backend/src/main/java/com/crm/common/ErrorCode.java`
- [ ] T005 [P] 创建 ChannelRequest/Response DTO in `backend/src/main/java/com/crm/dto/integration/`

**Checkpoint**: 数据模型就绪

---

## Phase 2: 用户故事 1 - 通道管理 (P0)

**Goal**: 通道 CRUD/启停/URL 校验。

**Independent Test**: 配置通道 → 列表 → 停用。

### 实现

- [ ] T006 [P] [US1] 创建 IntegrationChannelService（CRUD/启停/URL 校验）in `backend/src/main/java/com/crm/service/IntegrationChannelService.java`
- [ ] T007 [US1] 创建 IntegrationChannelController（/integration-channels，仅 ADMIN）in `backend/src/main/java/com/crm/controller/IntegrationChannelController.java`
- [ ] T008 [US1] 创建 IntegrationChannelServiceTest 单元测试 in `backend/src/test/java/com/crm/service/IntegrationChannelServiceTest.java`

**Checkpoint**: US1 可用——通道管理

---

## Phase 3: 用户故事 2 - 事件推送 (P0)

**Goal**: 业务事件推送 + 记录。

**Independent Test**: 工单分配 → 推送记录生成。

### 实现

- [ ] T009 [P] [US2] IntegrationChannelService 增加 publish(eventType, title)（遍历启用通道调 055 WebhookService）in `backend/src/main/java/com/crm/service/IntegrationChannelService.java`
- [ ] T010 [US2] 推送记录查询端点（/integration-channels/{id}/deliveries）in `backend/src/main/java/com/crm/controller/IntegrationChannelController.java`
- [ ] T011 [US2] 事件发布点：TicketService.assign（TICKET_ASSIGNED）in `backend/src/main/java/com/crm/service/TicketService.java`
- [ ] T012 [US2] 事件发布点：LeadService.create（LEAD_CREATED，复用 055 处扩展）in `backend/src/main/java/com/crm/service/LeadService.java`
- [ ] T013 [US2] 事件发布点：ApprovalEngineService.start（APPROVAL_PENDING）in `backend/src/main/java/com/crm/service/ApprovalEngineService.java`
- [ ] T014 [US2] 创建 IntegrationHubIT 集成测试 in `backend/src/test/java/com/crm/integration/IntegrationHubIT.java`
- [ ] T015 [US2] 前端类型/服务 in `frontend/src/types/integration.ts` + `frontend/src/services/integrationService.ts`
- [ ] T016 [US2] 集成中心页 in `frontend/src/pages/settings/IntegrationHubPage.tsx`（通道管理 + 推送记录）

**Checkpoint**: US2 可用——事件推送

---

## Phase 4: 收尾与验证

- [ ] T017 App.tsx 点亮"集成中心"占位项为路由 in `frontend/src/App.tsx`（/integration-hub）
- [ ] T018 Backend `mvn verify` 通过（含新增测试、spotless、JaCoCo）
- [ ] T019 单独运行 `mvn test "-Dtest=IntegrationHubIT"` 通过
- [ ] T020 Frontend typecheck / lint / test / build 通过
- [ ] T021 线上端点验证（通道 CRUD→工单分配→推送记录→停用不推送）
- [ ] T022 更新契约文档（按实现校正）与 roadmap 058 标记 `[x]`

**Checkpoint**: 模块完整可用

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1**: 依赖 055（复用推送）
- **Phase 2 (US1)**: 依赖 Phase 1
- **Phase 3 (US2)**: 依赖 Phase 1/2
- **Phase 4**: 依赖全部完成

## Notes

- 复用 055 WebhookService 推送/记录
- 事件子集：TICKET_ASSIGNED / LEAD_CREATED / APPROVAL_PENDING
- 提交规范：Conventional Commits
