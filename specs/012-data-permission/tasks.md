# Tasks: 数据权限增强模块

**Input**: Design documents from `/specs/012-data-permission/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2/US3

---

## Phase 1: 数据模型与基础

- [x] T001 [P] 创建 Flyway 迁移 V24 in `backend/src/main/resources/db/migration/V24__department.sql`（department 表 + parent 索引）
- [x] T002 [P] 创建 Flyway 迁移 V25 in `backend/src/main/resources/db/migration/V25__user_data_scope.sql`（user 表 department_id/data_scope 列）
- [x] T003 [P] 创建 Flyway 迁移 V26 in `backend/src/main/resources/db/migration/V26__customer_share.sql`（customer_share 表 + active_key 唯一 + 索引）
- [x] T004 [P] 创建 Department 实体/Mapper in `backend/src/main/java/com/crm/entity/Department.java` + `backend/src/main/java/com/crm/repository/DepartmentMapper.java`
- [x] T005 [P] 创建 CustomerShare 实体/Mapper in `backend/src/main/java/com/crm/entity/CustomerShare.java` + `backend/src/main/java/com/crm/repository/CustomerShareMapper.java`
- [x] T006 [P] User 实体新增 departmentId/dataScope in `backend/src/main/java/com/crm/entity/User.java`
- [x] T007 [P] H2 测试 schema 同步三处 in `backend/src/test/resources/schema-h2.sql`
- [x] T008 [P] ErrorCode 新增 DEPARTMENT_NOT_FOUND/DEPARTMENT_HAS_CHILDREN_OR_MEMBERS/SHARE_EXISTS/SHARE_NOT_FOUND in `backend/src/main/java/com/crm/common/ErrorCode.java`

**Checkpoint**: 数据模型就绪

---

## Phase 2: 用户故事 1 - 部门架构管理 (P0) 🎯 MVP

**Goal**: 部门 CRUD + 树 + 用户部门归属。

**Independent Test**: 建部门→子部门→树正确→删除防护。

### 实现

- [x] T009 [P] [US1] 创建 DepartmentRequest/DepartmentResponse/DepartmentTreeNode DTO in `backend/src/main/java/com/crm/dto/department/`
- [x] T010 [US1] 创建 DepartmentService in `backend/src/main/java/com/crm/service/DepartmentService.java`（CRUD/树构建/子孙集合/删除防护：子部门或成员 409）
- [x] T011 [US1] 创建 DepartmentController in `backend/src/main/java/com/crm/controller/DepartmentController.java`（GET tree、POST/PUT/DELETE，仅 ADMIN）
- [x] T012 [US1] UserService 增加部门/数据权限设置（PUT /users/{id}/data-permission）in `backend/src/main/java/com/crm/service/UserService.java` + `backend/src/main/java/com/crm/controller/UserController.java`
- [x] T013 [US1] 创建 DepartmentServiceTest 单元测试 in `backend/src/test/java/com/crm/service/DepartmentServiceTest.java`（树/子孙/删除防护）
- [x] T014 [US1] 创建 DataPermissionIT 集成测试（部门部分）in `backend/src/test/java/com/crm/integration/DataPermissionIT.java`
- [x] T015 [US1] 前端类型/服务 in `frontend/src/types/department.ts` + `frontend/src/services/departmentService.ts`
- [x] T016 [US1] 部门管理页 in `frontend/src/pages/departments/DepartmentListPage.tsx`（树 + 新增/编辑/删除，仅 ADMIN）
- [x] T017 [US1] 用户管理页增加部门/数据权限设置 in `frontend/src/pages/users/UserManagementPage.tsx`

**Checkpoint**: US1 可用——部门架构

---

## Phase 3: 用户故事 2 - 行级数据权限 (P0)

**Goal**: scope→可见 owner 集合解析 + 客户列表/详情/写操作过滤。

**Independent Test**: 配置不同 scope→客户列表过滤正确→越权 403。

### 实现

- [x] T018 [US2] 创建 DataPermissionService in `backend/src/main/java/com/crm/service/DataPermissionService.java`（resolveVisibleOwnerIds：SELF/DEPT/DEPT_AND_CHILD/ALL；ALL=空集合不过滤）
- [x] T019 [US2] CustomerService.page 应用权限过滤（owner IN visible 或 ALL 不过滤；公海视图不受影响）in `backend/src/main/java/com/crm/service/CustomerService.java`
- [x] T020 [US2] CustomerService 详情/编辑/删除校验权限（owner 可见或共享只读；越权 403）in `backend/src/main/java/com/crm/service/CustomerService.java`
- [x] T021 [US2] 创建 DataPermissionServiceTest 单元测试 in `backend/src/test/java/com/crm/service/DataPermissionServiceTest.java`（四档 scope 解析）
- [x] T022 [US2] CustomerServiceTest 适配（权限过滤注入）in `backend/src/test/java/com/crm/service/CustomerServiceTest.java`
- [x] T023 [US2] DataPermissionIT 增加行级过滤用例（SELF/DEPT/DEPT_AND_CHILD/ALL/越权 403）in `backend/src/test/java/com/crm/integration/DataPermissionIT.java`

**Checkpoint**: US2 可用——行级权限

---

## Phase 4: 用户故事 3 - 客户共享 (P1)

**Goal**: 共享/取消共享 + 共享给我列表（只读）。

**Independent Test**: 共享→对方可见→取消→不可见。

### 实现

- [x] T024 [P] [US3] 创建 CustomerShareRequest/SharedCustomerResponse DTO in `backend/src/main/java/com/crm/dto/share/`
- [x] T025 [US3] 创建 CustomerShareService in `backend/src/main/java/com/crm/service/CustomerShareService.java`（共享（归属者/ADMIN 校验+去重 409）/取消/共享给我列表）
- [x] T026 [US3] 创建 CustomerShareController in `backend/src/main/java/com/crm/controller/CustomerShareController.java`（POST/DELETE/GET shared-to-me）
- [x] T027 [US3] 创建 CustomerShareServiceTest 单元测试 in `backend/src/test/java/com/crm/service/CustomerShareServiceTest.java`（非归属者 403/去重 409/取消）
- [x] T028 [US3] DataPermissionIT 增加共享用例（共享→可见→只读 403→取消）in `backend/src/test/java/com/crm/integration/DataPermissionIT.java`
- [x] T029 [US3] 前端客户共享类型/服务 in `frontend/src/types/customerShare.ts` + `frontend/src/services/customerShareService.ts`
- [x] T030 [US3] 客户详情页增加共享管理（共享/取消/共享给我列表入口）in `frontend/src/pages/customers/CustomerDetailPage.tsx`

**Checkpoint**: US3 可用——客户共享

---

## Phase 5: 收尾与验证

- [x] T031 前端路由与菜单 in `frontend/src/App.tsx`（部门菜单仅 ADMIN）
- [x] T032 Backend `mvn verify` 通过（含新增测试、spotless、JaCoCo）
- [x] T033 单独运行 `mvn test "-Dtest=DataPermissionIT"` 通过
- [x] T034 Frontend typecheck / lint / test / build 通过
- [x] T035 线上端点验证（部门树→用户权限配置→不同 scope 客户列表→共享→只读 403→取消→权限 403）
- [x] T036 更新契约文档（按实现校正）与 roadmap 012 标记 `[x]`

**Checkpoint**: 模块完整可用
