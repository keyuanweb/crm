# Tasks: 自定义对象模块

**Input**: Design documents from `/specs/059-custom-object/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2

---

## Phase 1: 数据模型与基础

- [ ] T001 [P] 创建 Flyway 迁移 V67 in `backend/src/main/resources/db/migration/V67__custom_object.sql`（2 表）
- [ ] T002 [P] H2 测试 schema 同步 in `backend/src/test/resources/schema-h2.sql`
- [ ] T003 [P] 创建 CustomObject/CustomObjectRecord 实体 + 2 Mapper in `backend/src/main/java/com/crm/entity/` + `backend/src/main/java/com/crm/repository/`
- [ ] T004 [P] ErrorCode 新增 OBJECT_* 错误码 in `backend/src/main/java/com/crm/common/ErrorCode.java`
- [ ] T005 [P] 创建 DTO in `backend/src/main/java/com/crm/dto/customobject/`

**Checkpoint**: 数据模型就绪

---

## Phase 2: 用户故事 1 - 对象定义 (P0)

**Goal**: 对象 CRUD/字段校验/编码唯一。

**Independent Test**: 定义对象 → 校验字段 → 启用。

### 实现

- [ ] T006 [P] [US1] 创建 CustomObjectService（CRUD/字段校验/启停）in `backend/src/main/java/com/crm/service/CustomObjectService.java`
- [ ] T007 [US1] 创建 CustomObjectController（对象端点，仅 ADMIN）in `backend/src/main/java/com/crm/controller/CustomObjectController.java`
- [ ] T008 [US1] 创建 CustomObjectServiceTest 单元测试 in `backend/src/test/java/com/crm/service/CustomObjectServiceTest.java`

**Checkpoint**: US1 可用——对象定义

---

## Phase 3: 用户故事 2 - 记录管理 (P0)

**Goal**: 记录 CRUD/必填校验/搜索。

**Independent Test**: 创建记录 → 列表 → 编辑 → 删除。

### 实现

- [ ] T009 [P] [US2] 创建 CustomObjectRecordService（CRUD/必填校验/搜索/停用拦截）in `backend/src/main/java/com/crm/service/CustomObjectRecordService.java`
- [ ] T010 [US2] CustomObjectController 记录端点（/custom-objects/{id}/records，ADMIN+SALES）in `backend/src/main/java/com/crm/controller/CustomObjectController.java`
- [ ] T011 [US2] 创建 CustomObjectRecordServiceTest 单元测试 in `backend/src/test/java/com/crm/service/CustomObjectRecordServiceTest.java`
- [ ] T012 [US2] 创建 CustomObjectIT 集成测试 in `backend/src/test/java/com/crm/integration/CustomObjectIT.java`
- [ ] T013 [US2] 前端类型/服务 in `frontend/src/types/customObject.ts` + `frontend/src/services/customObjectService.ts`
- [ ] T014 [US2] 对象定义页 in `frontend/src/pages/custom-object/CustomObjectListPage.tsx`
- [ ] T015 [US2] 记录管理页（动态表单）in `frontend/src/pages/custom-object/CustomObjectRecordPage.tsx`

**Checkpoint**: US2 可用——记录管理

---

## Phase 4: 收尾与验证

- [ ] T016 App.tsx 新增"自定义对象"菜单与路由 in `frontend/src/App.tsx`（/custom-objects）
- [ ] T017 Backend `mvn verify` 通过（含新增测试、spotless、JaCoCo）
- [ ] T018 单独运行 `mvn test "-Dtest=CustomObjectIT"` 通过
- [ ] T019 Frontend typecheck / lint / test / build 通过
- [ ] T020 线上端点验证（对象定义→记录 CRUD→必填/停用拦截）
- [ ] T021 更新契约文档（按实现校正）与 roadmap 059 标记 `[x]`

**Checkpoint**: 模块完整可用

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1**: 无依赖
- **Phase 2 (US1)**: 依赖 Phase 1
- **Phase 3 (US2)**: 依赖 Phase 1/2
- **Phase 4**: 依赖全部完成

## Notes

- 字段集内嵌对象定义 JSON
- 记录值 JSON 键值对
- 提交规范：Conventional Commits
