# Tasks: 字段级读写权限模块

**Input**: Design documents from `/specs/056-field-permission/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2

---

## Phase 1: 数据模型与基础

- [ ] T001 [P] 创建 Flyway 迁移 V64 in `backend/src/main/resources/db/migration/V64__field_permission.sql`（field_permission 表 + uk_field_perm 唯一）
- [ ] T002 [P] H2 测试 schema 同步 in `backend/src/test/resources/schema-h2.sql`
- [ ] T003 [P] 创建 FieldPermission 实体 + Mapper in `backend/src/main/java/com/crm/entity/` + `backend/src/main/java/com/crm/repository/`
- [ ] T004 [P] ErrorCode 新增 FIELD_* 错误码 in `backend/src/main/java/com/crm/common/ErrorCode.java`
- [ ] T005 [P] 创建 FieldPermissionRequest/Response/FieldPermissionView DTO in `backend/src/main/java/com/crm/dto/field/`

**Checkpoint**: 数据模型就绪

---

## Phase 2: 用户故事 1 - 字段权限配置 (P0)

**Goal**: 配置 CRUD（角色×实体×字段 三态）。

**Independent Test**: 配置只读 → 生效。

### 实现

- [ ] T006 [P] [US1] 创建 FieldPermissionService（CRUD/upsert/校验）in `backend/src/main/java/com/crm/service/FieldPermissionService.java`
- [ ] T007 [US1] 创建 FieldPermissionController（/field-permissions，仅 ADMIN）in `backend/src/main/java/com/crm/controller/FieldPermissionController.java`
- [ ] T008 [US1] 创建 FieldPermissionServiceTest 单元测试 in `backend/src/test/java/com/crm/service/FieldPermissionServiceTest.java`

**Checkpoint**: US1 可用——配置

---

## Phase 3: 用户故事 2 - 权限应用 (P0)

**Goal**: 字段列表权限标记 + 保存拦截。

**Independent Test**: 只读/隐藏保存 422；ADMIN 豁免。

### 实现

- [ ] T009 [P] [US2] FieldPermissionService 增加 permissionFor(role, entity, field) + 校验方法 in `backend/src/main/java/com/crm/service/FieldPermissionService.java`
- [ ] T010 [US2] CustomFieldService 字段列表加权限标记 in `backend/src/main/java/com/crm/service/CustomFieldService.java`
- [ ] T011 [US2] CustomFieldService.saveValues 校验（HIDDEN/READ_ONLY 拦截）in `backend/src/main/java/com/crm/service/CustomFieldService.java`
- [ ] T012 [US2] 创建 FieldPermissionIT 集成测试 in `backend/src/test/java/com/crm/integration/FieldPermissionIT.java`
- [ ] T013 [US2] 前端类型/服务 in `frontend/src/types/fieldPermission.ts` + `frontend/src/services/fieldPermissionService.ts`
- [ ] T014 [US2] 配置页 in `frontend/src/pages/settings/FieldPermissionPage.tsx`（角色×实体×字段 矩阵）
- [ ] T015 [US2] 自定义字段表单接入权限（隐藏/禁用）in `frontend/src/components/`（CustomFieldForm）

**Checkpoint**: US2 可用——权限应用

---

## Phase 4: 收尾与验证

- [ ] T016 App.tsx 点亮"字段权限"占位项为路由 in `frontend/src/App.tsx`（/field-permissions）
- [ ] T017 Backend `mvn verify` 通过（含新增测试、spotless、JaCoCo）
- [ ] T018 单独运行 `mvn test "-Dtest=FieldPermissionIT"` 通过
- [ ] T019 Frontend typecheck / lint / test / build 通过
- [ ] T020 线上端点验证（配置→字段标记→只读保存 422→ADMIN 豁免）
- [ ] T021 更新契约文档（按实现校正）与 roadmap 056 标记 `[x]`

**Checkpoint**: 模块完整可用

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1**: 无依赖
- **Phase 2 (US1)**: 依赖 Phase 1
- **Phase 3 (US2)**: 依赖 Phase 1/2
- **Phase 4**: 依赖全部完成

## Notes

- 三态 HIDDEN/READ_ONLY/EDITABLE；ADMIN 豁免
- 服务端强制（saveValues 集中校验）
- 提交规范：Conventional Commits
