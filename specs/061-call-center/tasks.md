# Tasks: 通话记录管理模块

**Input**: Design documents from `/specs/061-call-center/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2

---

## Phase 1: 数据模型与基础

- [x] T001 [P] 创建 Flyway 迁移 V68 in `backend/src/main/resources/db/migration/V68__call_record.sql`
- [x] T002 [P] H2 测试 schema 同步 in `backend/src/test/resources/schema-h2.sql`
- [x] T003 [P] 创建 CallRecord 实体 + Mapper in `backend/src/main/java/com/crm/entity/` + `backend/src/main/java/com/crm/repository/`
- [x] T004 [P] ErrorCode 新增 CALL_* 错误码 in `backend/src/main/java/com/crm/common/ErrorCode.java`
- [x] T005 [P] 创建 DTO in `backend/src/main/java/com/crm/dto/call/`

**Checkpoint**: 数据模型就绪

---

## Phase 2: 用户故事 1 - 通话记录管理 (P0)

**Goal**: 记录 CRUD/枚举/归属校验。

**Independent Test**: 录入→列表→详情→删除。

### 实现

- [x] T006 [P] [US1] 创建 CallRecordService（CRUD/枚举校验/归属校验）in `backend/src/main/java/com/crm/service/CallRecordService.java`
- [x] T007 [US1] 创建 CallRecordController（/call-records）in `backend/src/main/java/com/crm/controller/CallRecordController.java`
- [x] T008 [US1] 创建 CallRecordServiceTest 单元测试 in `backend/src/test/java/com/crm/service/CallRecordServiceTest.java`

**Checkpoint**: US1 可用——记录管理

---

## Phase 3: 用户故事 2 - 通话统计 (P0)

**Goal**: 统计聚合。

**Independent Test**: 多记录→统计正确。

### 实现

- [x] T009 [P] [US2] CallRecordService 增加 stats 聚合 in `backend/src/main/java/com/crm/service/CallRecordService.java`
- [x] T010 [US2] CallRecordController 增加 /call-records/stats in `backend/src/main/java/com/crm/controller/CallRecordController.java`
- [x] T011 [US2] 创建 CallCenterIT 集成测试 in `backend/src/test/java/com/crm/integration/CallCenterIT.java`
- [x] T012 [US2] 前端类型/服务 in `frontend/src/types/callRecord.ts` + `frontend/src/services/callRecordService.ts`
- [x] T013 [US2] 通话记录页 in `frontend/src/pages/calls/CallRecordPage.tsx`（列表/录入/统计）

**Checkpoint**: US2 可用——统计

---

## Phase 4: 收尾与验证

- [x] T014 App.tsx 工作台组新增"通话记录"菜单与路由 in `frontend/src/App.tsx`（/call-records）
- [x] T015 Backend `mvn verify` 通过（含新增测试、spotless、JaCoCo）
- [x] T016 单独运行 `mvn test "-Dtest=CallCenterIT"` 通过
- [x] T017 Frontend typecheck / lint / test / build 通过
- [x] T018 线上端点验证（录入→列表→统计→归属校验）
- [x] T019 更新契约文档（按实现校正）与 roadmap 061 标记 `[x]`

**Checkpoint**: 模块完整可用

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1**: 无依赖
- **Phase 2 (US1)**: 依赖 Phase 1
- **Phase 3 (US2)**: 依赖 Phase 1
- **Phase 4**: 依赖全部完成

## Notes

- CTI 硬件自动接入走 POST /call-records（数据模型就绪）
- 提交规范：Conventional Commits
