# Tasks: 开放平台模块

**Input**: Design documents from `/specs/055-open-platform/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2

---

## Phase 1: 数据模型与基础

- [x] T001 [P] 创建 Flyway 迁移 V63 in `backend/src/main/resources/db/migration/V63__open_platform.sql`（3 表）
- [x] T002 [P] H2 测试 schema 同步 in `backend/src/test/resources/schema-h2.sql`
- [x] T003 [P] 创建 ApiKey/WebhookSubscription/WebhookDelivery 实体 + 3 Mapper in `backend/src/main/java/com/crm/entity/` + `backend/src/main/java/com/crm/repository/`
- [x] T004 [P] ErrorCode 新增 OPEN_* 错误码 in `backend/src/main/java/com/crm/common/ErrorCode.java`
- [x] T005 [P] 创建 DTO（ApiKeyRequest/Response/WebhookRequest/Response/DeliveryResponse）in `backend/src/main/java/com/crm/dto/open/`

**Checkpoint**: 数据模型就绪

---

## Phase 2: 用户故事 1 - API Key 管理 (P0)

**Goal**: Key 生成/哈希/吊销 + X-API-Key 鉴权。

**Independent Test**: 生成→携带调用→吊销失效。

### 实现

- [x] T006 [P] [US1] 创建 ApiKeyService（生成/校验哈希/吊销/使用记录）in `backend/src/main/java/com/crm/service/ApiKeyService.java`
- [x] T007 [US1] 创建 ApiKeyAuthFilter（X-API-Key → /api/v1/open/**）in `backend/src/main/java/com/crm/security/ApiKeyAuthFilter.java`
- [x] T008 [US1] SecurityConfig 注册过滤器与 /api/v1/open/** 规则 in `backend/src/main/java/com/crm/config/SecurityConfig.java`
- [x] T009 [US1] OpenPlatformController 管理端点（/platform/api-keys）+ 开放端点（/open/customers|leads）in `backend/src/main/java/com/crm/controller/OpenPlatformController.java`
- [x] T010 [US1] 创建 ApiKeyServiceTest 单元测试 in `backend/src/test/java/com/crm/service/ApiKeyServiceTest.java`

**Checkpoint**: US1 可用——API Key 鉴权

---

## Phase 3: 用户故事 2 - Webhook 订阅与推送 (P0)

**Goal**: 订阅/异步推送/签名/重试/记录。

**Independent Test**: 配置→触发→回调收到（验签）→记录。

### 实现

- [x] T011 [P] [US2] 创建 WebhookService（订阅/异步推送 HMAC/重试退避/记录）in `backend/src/main/java/com/crm/service/WebhookService.java`
- [x] T012 [US2] OpenPlatformController Webhook 管理端点（/platform/webhooks + deliveries）in `backend/src/main/java/com/crm/controller/OpenPlatformController.java`
- [x] T013 [US2] 业务事件发布点（LeadService.create/update、CustomerService.create 调 publish）in `backend/src/main/java/com/crm/service/`
- [x] T014 [US2] 创建 WebhookServiceTest 单元测试 in `backend/src/test/java/com/crm/service/WebhookServiceTest.java`
- [x] T015 [US2] 创建 OpenPlatformIT 集成测试 in `backend/src/test/java/com/crm/integration/OpenPlatformIT.java`
- [x] T016 [US2] 前端类型/服务 in `frontend/src/types/openPlatform.ts` + `frontend/src/services/openPlatformService.ts`
- [x] T017 [US2] API Key 管理页 in `frontend/src/pages/open/ApiKeyPage.tsx`
- [x] T018 [US2] Webhook 订阅页（含推送记录）in `frontend/src/pages/open/WebhookPage.tsx`

**Checkpoint**: US2 可用——Webhook 推送

---

## Phase 4: 收尾与验证

- [x] T019 App.tsx 点亮"开放平台"占位项为路由 in `frontend/src/App.tsx`（/open-platform）
- [x] T020 Backend `mvn verify` 通过（含新增测试、spotless、JaCoCo）
- [x] T021 单独运行 `mvn test "-Dtest=OpenPlatformIT"` 通过
- [x] T022 Frontend typecheck / lint / test / build 通过
- [x] T023 线上端点验证（Key 创建→鉴权→吊销 401→Webhook 触发→记录）
- [x] T024 更新契约文档（按实现校正）与 roadmap 055 标记 `[x]`

**Checkpoint**: 模块完整可用

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1**: 无依赖
- **Phase 2 (US1)**: 依赖 Phase 1
- **Phase 3 (US2)**: 依赖 Phase 1；事件发布点需既有 Service
- **Phase 4**: 依赖全部完成

## Notes

- Key 存哈希；仅创建返回完整值
- Webhook HMAC-SHA256 签名 + ≤3 次退避重试
- 提交规范：Conventional Commits
