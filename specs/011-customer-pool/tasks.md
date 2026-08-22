# Tasks: 客户公海与转移模块

**Input**: Design documents from `/specs/011-customer-pool/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2/US3

---

## Phase 1: 数据模型与基础

- [x] T001 [P] 创建 Flyway 迁移 V23 in `backend/src/main/resources/db/migration/V23__customer_owner.sql`（customer 表 owner_id 列 + idx_customer_deleted_owner 索引）
- [x] T002 [P] Customer 实体新增 ownerId 字段 in `backend/src/main/java/com/crm/entity/Customer.java`
- [x] T003 [P] H2 测试 schema 同步 owner_id 列 in `backend/src/test/resources/schema-h2.sql`
- [x] T004 [P] ErrorCode 新增 CUSTOMER_ALREADY_OWNED in `backend/src/main/java/com/crm/common/ErrorCode.java`
- [x] T005 [P] application.yml 增加 `crm.pool.stale-days`（默认 30）in `backend/src/main/resources/application.yml`

**Checkpoint**: 数据模型就绪

---

## Phase 2: 用户故事 1 - 客户归属与公海池 (P0) 🎯 MVP

**Goal**: 公海列表 + 领取 + 我的客户视图。

**Independent Test**: 建无归属客户→公海可见→领取→我的客户。

### 实现

- [x] T006 [P] [US1] 创建 CustomerPoolService in `backend/src/main/java/com/crm/service/CustomerPoolService.java`（公海分页/我的客户分页/领取（条件更新并发安全）/批量装配 ownerName）
- [x] T007 [US1] 创建 CustomerPoolController in `backend/src/main/java/com/crm/controller/CustomerPoolController.java`（GET /customers/pool、POST /customers/pool/{id}/claim、GET /customers/my）
- [x] T008 [US1] 创建 CustomerPoolServiceTest 单元测试 in `backend/src/test/java/com/crm/service/CustomerPoolServiceTest.java`（公海查询/领取成功/重复领取 409/ownerName 装配）
- [x] T009 [US1] 创建 CustomerPoolIT 集成测试（公海部分）in `backend/src/test/java/com/crm/integration/CustomerPoolIT.java`
- [x] T010 [US1] 前端类型/服务扩展 in `frontend/src/types/customer.ts`（ownerId/ownerName）+ `frontend/src/services/customerService.ts`（pool/my/claim）
- [x] T011 [US1] 客户列表页增加"我的/公海"视图切换与领取按钮 in `frontend/src/pages/customers/CustomerListPage.tsx`

**Checkpoint**: US1 可用——归属与公海

---

## Phase 3: 用户故事 2 - 超期自动退回公海 (P0)

**Goal**: 公海扫描（超 N 天未跟进退回）。

**Independent Test**: 构造超期客户→扫描→退回公海。

### 实现

- [x] T012 [US2] CustomerPoolService 增加 scan（活跃时间=MAX(follow_up.created_at) 或 customer.created_at，超 N 天退回；批次查询避免 N+1）in `backend/src/main/java/com/crm/service/CustomerPoolService.java`
- [x] T013 [US2] CustomerPoolController 增加 POST /customers/pool/scan（仅 ADMIN）in `backend/src/main/java/com/crm/controller/CustomerPoolController.java`
- [x] T014 [US2] CustomerPoolServiceTest 增加扫描用例（超期退回/近期保留/无跟进按创建时间）in `backend/src/test/java/com/crm/service/CustomerPoolServiceTest.java`
- [x] T015 [US2] CustomerPoolIT 增加扫描用例（含权限 403）in `backend/src/test/java/com/crm/integration/CustomerPoolIT.java`

**Checkpoint**: US2 可用——超期退回

---

## Phase 4: 用户故事 3 - 客户批量转移 (P1)

**Goal**: 批量转移/公海分配（≤100，目标用户校验）。

**Independent Test**: 批量转移→归属更新→目标不存在 404。

### 实现

- [x] T016 [P] [US3] 创建 BatchTransferRequest DTO in `backend/src/main/java/com/crm/dto/pool/`（customerIds ≤100 校验、targetOwnerId 必填）
- [x] T017 [US3] CustomerPoolService 增加 batchTransfer（目标用户存在校验/批量 UPDATE/审计）in `backend/src/main/java/com/crm/service/CustomerPoolService.java`
- [x] T018 [US3] CustomerPoolController 增加 POST /customers/batch-transfer（仅 ADMIN）in `backend/src/main/java/com/crm/controller/CustomerPoolController.java`
- [x] T019 [US3] CustomerPoolServiceTest 增加批量转移用例（成功/目标不存在 404/超限 400）in `backend/src/test/java/com/crm/service/CustomerPoolServiceTest.java`
- [x] T020 [US3] CustomerPoolIT 增加批量转移用例（含非 ADMIN 403）in `backend/src/test/java/com/crm/integration/CustomerPoolIT.java`
- [x] T021 [US3] 前端客户列表页增加批量转移/分配弹窗（仅管理员，多选客户+选目标用户）in `frontend/src/pages/customers/CustomerListPage.tsx`

**Checkpoint**: US3 可用——批量转移

---

## Phase 5: 收尾与验证

- [x] T022 Backend `mvn verify` 通过（含新增测试、spotless、JaCoCo）
- [x] T023 单独运行 `mvn test "-Dtest=CustomerPoolIT"` 通过
- [x] T024 Frontend typecheck / lint / test / build 通过
- [x] T025 线上端点验证（公海列表→领取→我的客户→重复领取 409→扫描退回→批量转移→权限 403）
- [x] T026 更新契约文档（按实现校正）与 roadmap 011 标记 `[x]`

**Checkpoint**: 模块完整可用
