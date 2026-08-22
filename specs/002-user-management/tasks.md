# Tasks: 用户管理模块

**Input**: Design documents from `/specs/002-user-management/`

**Prerequisites**: plan.md (required), spec.md (required for user stories)

**Organization**: Tasks are grouped by user story (002-user-management).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1/US2/US3)
- Include exact file paths in descriptions

## Path Conventions

- 沿用 001-crm-core：`backend/src/`、`frontend/src/`

---

## Phase 1: Setup & Foundational

- [x] T001 Create Flyway 迁移 `backend/src/main/resources/db/migration/V4__user_auth_fields.sql`（user 表新增 last_login_at / token_version）
- [x] T002 Update User 实体（lastLoginAt/tokenVersion）in `backend/src/main/java/com/crm/entity/User.java`
- [x] T003 JWT 令牌携带 tokenVersion（tv 声明）in `backend/src/main/java/com/crm/security/JwtUtil.java`
- [x] T004 认证过滤器逐请求校验账号状态与令牌版本（停用即时生效、旧令牌失效）in `backend/src/main/java/com/crm/security/JwtAuthFilter.java`
- [x] T005 登录成功更新 lastLoginAt in `backend/src/main/java/com/crm/service/AuthService.java`

**Checkpoint**: 基础设施就绪（密码变更/停用可即时失效令牌）

---

## Phase 2: User Story 1 - 用户开通与登录 (Priority: P1) 🎯 MVP

**Goal**: 管理员创建用户（用户名/显示名/角色/初始密码），新用户可立即登录（FR-001~003）

**Independent Test**: 管理员建号 → 新用户登录成功；重复用户名被拒绝

- [x] T006 [P] [US1] Create User DTOs in `backend/src/main/java/com/crm/dto/user/`（UserCreateRequest/UserUpdateRequest/ResetPasswordRequest/ChangePasswordRequest/UserResponse）
- [x] T007 [P] [US1] Create 用户管理契约测试 in `backend/src/test/java/com/crm/integration/UserIT.java`
- [x] T008 [US1] Implement UserService（创建/去重/密码强度校验/bcrypt 加密）in `backend/src/main/java/com/crm/service/UserService.java`
- [x] T009 [US1] Implement UserController（GET/POST /api/v1/users，ADMIN 权限）in `backend/src/main/java/com/crm/controller/UserController.java`

**Checkpoint**: US1 可用（S1 场景）

---

## Phase 3: User Story 2 - 用户检索与列表 (Priority: P1)

**Goal**: 分页/关键字/角色筛选的用户列表（FR-001/FR-002）

**Independent Test**: 多用户数据下搜索、筛选、分页正确

- [x] T010 [US2] Backend: 关键字+角色筛选与分页 in `backend/src/main/java/com/crm/service/UserService.java`
- [x] T011 [US2] Frontend: 用户管理页（列表/搜索/筛选/分页/创建/编辑/重置密码/启停）in `frontend/src/pages/users/UserManagementPage.tsx` + `frontend/src/services/userService.ts` + `frontend/src/types/user.ts`
- [x] T012 [US2] Frontend: 路由与管理员导航入口 in `frontend/src/App.tsx`

**Checkpoint**: US1 + US2 可用

---

## Phase 4: User Story 3 - 编辑、启停与密码重置 (Priority: P2)

**Goal**: 编辑（显示名/角色）、启停、管理员重置密码、本人改密（FR-004~008）

**Independent Test**: 角色变更即时生效、停用后无法登录、重置/改密后旧令牌失效

- [x] T013 [P] [US3] Backend: update/resetPassword/changeOwnPassword + 防护规则（禁停自己/保留至少一个启用 ADMIN）in `backend/src/main/java/com/crm/service/UserService.java`
- [x] T014 [P] [US3] Backend: 令牌失效（token_version 递增 + Redis 刷新令牌清理）in `backend/src/main/java/com/crm/service/UserService.java`
- [x] T015 [US3] Backend: 单元测试 in `backend/src/test/java/com/crm/service/UserServiceTest.java`（去重/弱密码/禁停自己/最后一个 ADMIN/旧密码错误）
- [x] T016 [US3] Backend: 集成测试 in `backend/src/test/java/com/crm/integration/UserIT.java`（创建→登录→角色→重置→旧令牌失效、停用即时失效、重复用户名、弱密码）
- [x] T017 [US3] Frontend: 修改密码页 in `frontend/src/pages/account/ChangePasswordPage.tsx` + `frontend/src/App.tsx` 路由
- [x] T018 审计接入：用户创建/编辑/重置/改密写入 audit_log in `backend/src/main/java/com/crm/service/UserService.java`（FR-017 一致性）

**Checkpoint**: 全部用户故事可用（S1~S4 场景）

---

## 验证

- [x] Backend `mvn verify`：22 测试通过（含 UserServiceTest 5 + UserIT 4）、spotless、JaCoCo 门禁通过
- [x] Frontend `npm run typecheck` / `lint` / `test` / `build` 通过

## Notes

- 用户名不可修改、不提供物理删除（停用代替），与审计数据保持一致。
- 依赖 001-crm-core 的认证框架与审计服务（FR-017）。

---

## Phase 5: Convergence

**Purpose**: `/speckit-converge`（2026-08-22）追加的剩余工作

- [ ] T019 为用户管理页与修改密码页补前端组件测试（列表/创建/编辑/重置密码/启停、改密流程）in `frontend/src/pages/users/UserManagementPage.test.tsx`、`frontend/src/pages/account/ChangePasswordPage.test.tsx` per Constitution IV (partial)
- [ ] T020 增加用户管理 Playwright e2e 冒烟（管理员建号→新用户登录→停用→旧令牌失效）in `frontend/e2e/user-management.spec.ts` per FR-001~008 验收场景 (missing)
