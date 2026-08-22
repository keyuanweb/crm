# Tasks: 联系人管理模块

**Input**: Design documents from `/specs/005-contact-management/`

**Prerequisites**: plan.md (required), spec.md (required)

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2

---

## Phase 1: 数据模型

- [x] T001 [P] 创建 Flyway 迁移 V10 in `backend/src/main/resources/db/migration/V10__contact.sql`
- [x] T002 [P] 创建 Contact 实体 in `backend/src/main/java/com/crm/entity/Contact.java`
- [x] T003 [P] 创建 ContactMapper in `backend/src/main/java/com/crm/repository/ContactMapper.java`
- [x] T004 [P] H2 测试 schema 同步 contact 表 in `backend/src/test/resources/schema-h2.sql`

**Checkpoint**: 数据模型就绪

---

## Phase 2: DTO 与错误码

- [x] T005 [P] 创建 Contact DTOs in `backend/src/main/java/com/crm/dto/contact/`（ContactRequest/ContactResponse）
- [x] T006 [P] ErrorCode 新增 CONTACT_NOT_FOUND / CONTACT_DUPLICATE

**Checkpoint**: DTO 就绪

---

## Phase 3: User Story 1 - 联系人维护 (P0)

- [x] T007 [P] [US1] 创建 ContactService（CRUD/逻辑删除/同客户姓名+电话唯一校验/客户存在校验）
- [x] T008 [P] [US1] 创建 ContactController（GET/POST/PUT/DELETE /api/v1/contacts）
- [x] T009 [US1] CustomerService.detail 聚合该客户联系人列表 + CustomerDetailResponse 增加 contacts 字段

**Checkpoint**: US1 后端可用

---

## Phase 4: User Story 2 - 检索与详情 (P0)

- [x] T010 [P] [US2] ContactService 列表支持关键字/客户/角色筛选与分页（批量装配客户名，无 N+1）
- [x] T011 [P] [US2] 后端集成测试 ContactIT（CRUD/搜索筛选/唯一性 409/客户不存在 404/逻辑删除）
- [x] T012 [US2] 后端单元测试 ContactServiceTest

**Checkpoint**: US2 后端可用

---

## Phase 5: 前端实现

- [x] T013 [P] 前端类型与 API 服务 in `frontend/src/types/contact.ts`、`frontend/src/services/contactService.ts`
- [x] T014 [P] 联系人列表页 in `frontend/src/pages/contacts/ContactListPage.tsx`（ProTable + 搜索筛选 + 新增/编辑/删除弹窗）
- [x] T015 [P] 客户详情集成联系人卡片（列表 + 添加/编辑/删除）in `frontend/src/pages/customers/CustomerDetailPage.tsx`
- [x] T016 前端路由与菜单 in `frontend/src/App.tsx`（联系人菜单 + /contacts 路由）

**Checkpoint**: 前端完整可用

---

## 验证

- [x] Backend `mvn verify` 通过（含新增测试、spotless、JaCoCo）
- [x] Frontend typecheck / lint / test / build 通过
- [x] 线上端点验证（列表/创建/唯一性/客户详情联系人）
