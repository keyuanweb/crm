# Tasks: 邮件高级能力模块

**Input**: Design documents from `/specs/052-email-advanced/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2/US3

---

## Phase 1: 数据模型与基础

- [ ] T001 [P] 创建 Flyway 迁移 V60 in `backend/src/main/resources/db/migration/V60__email_unsubscribe.sql`（email_unsubscribe 表 + email_campaign.variant/subject_b/winner + email_send_log.variant）
- [ ] T002 [P] H2 测试 schema 同步 in `backend/src/test/resources/schema-h2.sql`
- [ ] T003 [P] 创建 EmailUnsubscribe 实体 + Mapper in `backend/src/main/java/com/crm/entity/` + `backend/src/main/java/com/crm/repository/`
- [ ] T004 [P] EmailCampaign/EmailSendLog 实体加 variant/subjectB/winner 字段 in `backend/src/main/java/com/crm/entity/`
- [ ] T005 [P] ErrorCode 新增 EMAIL_* 错误码 in `backend/src/main/java/com/crm/common/ErrorCode.java`

**Checkpoint**: 数据模型就绪

---

## Phase 2: 用户故事 1 - 邮件退订管理 (P0)

**Goal**: 退订记录/公开端点/名单/排除。

**Independent Test**: 退订→名单→群发排除→恢复。

### 实现

- [ ] T006 [P] [US1] 创建 EmailUnsubscribeService（退订/名单/恢复/排除检查）in `backend/src/main/java/com/crm/service/EmailUnsubscribeService.java`
- [ ] T007 [US1] 公开退订端点（/api/v1/public/email/unsubscribe）in `backend/src/main/java/com/crm/controller/EmailUnsubscribeController.java`
- [ ] T008 [US1] EmailController 退订管理端点（名单/恢复）in `backend/src/main/java/com/crm/controller/EmailController.java`
- [ ] T009 [US1] EmailCampaignService 群发排除退订邮箱 in `backend/src/main/java/com/crm/service/EmailCampaignService.java`
- [ ] T010 [US1] 049 自动化发信排除退订 in `backend/src/main/java/com/crm/service/EmailCampaignService.java`（sendAutomationEmail）
- [ ] T011 [US1] 创建 EmailUnsubscribeServiceTest 单元测试 in `backend/src/test/java/com/crm/service/EmailUnsubscribeServiceTest.java`

**Checkpoint**: US1 可用——退订管理

---

## Phase 3: 用户故事 2/3 - 统计与 A-B 测试 (P0)

**Goal**: 送达/打开/点击统计 + A-B 主题分组与标记。

**Independent Test**: 群发→统计正确；A/B→两变体各 50%→标记优者。

### 实现

- [ ] T012 [P] [US2] EmailCampaignService 统计聚合（sent/failed/open/click + 率）in `backend/src/main/java/com/crm/service/EmailCampaignService.java`
- [ ] T013 [P] [US3] EmailCampaignService A-B 分组（variant=AB 按收件人 id 奇偶 + subjectB）in `backend/src/main/java/com/crm/service/EmailCampaignService.java`
- [ ] T014 [US2/3] EmailController 统计端点（/email/campaigns/{id}/stats）+ 创建 A/B 扩展 in `backend/src/main/java/com/crm/controller/EmailController.java`
- [ ] T015 [US2/3] 创建 EmailAdvancedIT 集成测试 in `backend/src/test/java/com/crm/integration/EmailAdvancedIT.java`
- [ ] T016 [US2/3] 前端类型/服务扩展 in `frontend/src/types/email.ts` + `frontend/src/services/emailService.ts`
- [ ] T017 [US2/3] 退订名单页 in `frontend/src/pages/email/EmailUnsubscribePage.tsx`
- [ ] T018 [US2/3] 群发详情统计展示 + 创建 A/B 配置 in `frontend/src/pages/email/`

**Checkpoint**: US2/3 可用——统计与 A-B

---

## Phase 4: 收尾与验证

- [ ] T019 App.tsx 营销中心组点亮"邮件退订"菜单（可选，或并入邮件营销页）in `frontend/src/App.tsx`
- [ ] T020 Backend `mvn verify` 通过（含新增测试、spotless、JaCoCo）
- [ ] T021 单独运行 `mvn test "-Dtest=EmailAdvancedIT"` 通过
- [ ] T022 Frontend typecheck / lint / test / build 通过
- [ ] T023 线上端点验证（退订→名单→群发排除→统计）
- [ ] T024 更新契约文档（按实现校正）与 roadmap 052 标记 `[x]`

**Checkpoint**: 模块完整可用

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1**: 依赖 030 实体
- **Phase 2 (US1)**: 依赖 Phase 1
- **Phase 3 (US2/3)**: 依赖 Phase 1；统计复用发送日志
- **Phase 4**: 依赖全部完成

## Notes

- 退订按邮箱匹配；公开端点免登录
- A/B 仅主题变体 + 标记更优（不自动切流量）
- 提交规范：Conventional Commits
