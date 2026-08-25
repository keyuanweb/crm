# Tasks: 邮件账户与同步记录模块

**Input**: Design documents from `/specs/062-email-sync/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2

---

## Phase 1: 数据模型与基础

- [ ] T001 [P] 创建 Flyway 迁移 V69 in `backend/src/main/resources/db/migration/V69__mail_sync.sql`（2 表）
- [ ] T002 [P] H2 测试 schema 同步 in `backend/src/test/resources/schema-h2.sql`
- [ ] T003 [P] 创建 MailAccount/MailSyncRecord 实体 + 2 Mapper in `backend/src/main/java/com/crm/entity/` + `backend/src/main/java/com/crm/repository/`
- [ ] T004 [P] ErrorCode 新增 MAIL_* 错误码 in `backend/src/main/java/com/crm/common/ErrorCode.java`
- [ ] T005 [P] 创建 DTO in `backend/src/main/java/com/crm/dto/mail/`

**Checkpoint**: 数据模型就绪

---

## Phase 2: 用户故事 1 - 邮件账户配置 (P0)

**Goal**: 账户 CRUD/邮箱校验/默认唯一。

**Independent Test**: 配置账户 → 默认唯一 → 删除。

### 实现

- [ ] T006 [P] [US1] 创建 MailAccountService（CRUD/邮箱校验/默认唯一）in `backend/src/main/java/com/crm/service/MailAccountService.java`
- [ ] T007 [US1] 创建 MailAccountController（账户端点，仅 ADMIN）in `backend/src/main/java/com/crm/controller/MailAccountController.java`
- [ ] T008 [US1] 创建 MailAccountServiceTest 单元测试 in `backend/src/test/java/com/crm/service/MailAccountServiceTest.java`

**Checkpoint**: US1 可用——账户配置

---

## Phase 3: 用户故事 2 - 同步记录与模拟同步 (P0)

**Goal**: 模拟同步生成记录/列表/删除。

**Independent Test**: 模拟同步 → 记录 → 删除。

### 实现

- [ ] T009 [P] [US2] 创建 MailSyncRecordService（列表/模拟同步/删除）in `backend/src/main/java/com/crm/service/MailSyncRecordService.java`
- [ ] T010 [US2] MailAccountController 同步端点（/mail-accounts/{id}/sync + /records，ADMIN+SALES）in `backend/src/main/java/com/crm/controller/MailAccountController.java`
- [ ] T011 [US2] 创建 MailSyncRecordServiceTest 单元测试 in `backend/src/test/java/com/crm/service/MailSyncRecordServiceTest.java`
- [ ] T012 [US2] 创建 EmailSyncIT 集成测试 in `backend/src/test/java/com/crm/integration/EmailSyncIT.java`
- [ ] T013 [US2] 前端类型/服务 in `frontend/src/types/mail.ts` + `frontend/src/services/mailService.ts`
- [ ] T014 [US2] 账户配置页 in `frontend/src/pages/mail/MailAccountPage.tsx`
- [ ] T015 [US2] 同步记录页 in `frontend/src/pages/mail/MailSyncRecordPage.tsx`

**Checkpoint**: US2 可用——同步链路

---

## Phase 4: 收尾与验证

- [ ] T016 App.tsx 工作台组新增"邮件同步"菜单与路由 in `frontend/src/App.tsx`（/mail-sync）
- [ ] T017 Backend `mvn verify` 通过（含新增测试、spotless、JaCoCo）
- [ ] T018 单独运行 `mvn test "-Dtest=EmailSyncIT"` 通过
- [ ] T019 Frontend typecheck / lint / test / build 通过
- [ ] T020 线上端点验证（账户 CRUD→默认唯一→模拟同步→记录）
- [ ] T021 更新契约文档（按实现校正）与 roadmap 062 标记 `[x]`

**Checkpoint**: 模块完整可用

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1**: 无依赖
- **Phase 2 (US1)**: 依赖 Phase 1
- **Phase 3 (US2)**: 依赖 Phase 1/2
- **Phase 4**: 依赖全部完成

## Notes

- v1 模拟同步（不接真实 IMAP/OAuth）
- 默认发件唯一；提交规范：Conventional Commits
