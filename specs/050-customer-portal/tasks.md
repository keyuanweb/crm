# Tasks: 客户自助门户模块

**Input**: Design documents from `/specs/050-customer-portal/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2

---

## Phase 1: 用户故事 1 - 知识库自助浏览 (P0)

**Goal**: 公开文章列表/搜索/详情（仅 PUBLISHED）。

**Independent Test**: 未登录浏览/搜索/查看详情；草稿不可见。

### 实现

- [ ] T001 [P] [US1] 创建 PortalArticleResponse DTO in `backend/src/main/java/com/crm/dto/portal/`
- [ ] T002 [US1] 创建 CustomerPortalService（文章列表/详情，仅 PUBLISHED）in `backend/src/main/java/com/crm/service/CustomerPortalService.java`
- [ ] T003 [US1] 创建 CustomerPortalController（/portal/articles）in `backend/src/main/java/com/crm/controller/CustomerPortalController.java`
- [ ] T004 [US1] ErrorCode 新增 PORTAL_* 错误码 in `backend/src/main/java/com/crm/common/ErrorCode.java`
- [ ] T005 [US1] SecurityConfig 白名单加 /api/v1/portal/** in `backend/src/main/java/com/crm/config/SecurityConfig.java`

**Checkpoint**: US1 可用——知识库门户

---

## Phase 2: 用户故事 2 - 在线提单与进度查询 (P0)

**Goal**: 提单（识别客户）+ 查询码 + 进度查询（双验证）。

**Independent Test**: 提单→工单号→凭号+手机/邮箱查进度。

### 实现

- [ ] T006 [P] [US2] 创建 PortalTicketRequest/PortalTicketStatusResponse DTO in `backend/src/main/java/com/crm/dto/portal/`
- [ ] T007 [US2] CustomerPortalService 提单（手机/邮箱识别联系人→customerId→TicketService.create）in `backend/src/main/java/com/crm/service/CustomerPortalService.java`
- [ ] T008 [US2] CustomerPortalService 进度查询（工单号+联系人验证→状态/回复）in `backend/src/main/java/com/crm/service/CustomerPortalService.java`
- [ ] T009 [US2] CustomerPortalController（/portal/tickets + /portal/tickets/status）in `backend/src/main/java/com/crm/controller/CustomerPortalController.java`
- [ ] T010 [US2] 创建 CustomerPortalServiceTest 单元测试 in `backend/src/test/java/com/crm/service/CustomerPortalServiceTest.java`
- [ ] T011 [US2] 创建 CustomerPortalIT 集成测试 in `backend/src/test/java/com/crm/integration/CustomerPortalIT.java`
- [ ] T012 [US2] 前端类型/服务 in `frontend/src/types/portal.ts` + `frontend/src/services/customerPortalService.ts`
- [ ] T013 [US2] 门户页（知识库浏览/提单/进度查询）in `frontend/src/pages/portal/`
- [ ] T014 [US2] App.tsx 公开路由 /portal（绕过 RequireAuth）in `frontend/src/App.tsx`

**Checkpoint**: US2 可用——提单与进度

---

## Phase 3: 收尾与验证

- [ ] T015 Backend `mvn verify` 通过（含新增测试、spotless、JaCoCo）
- [ ] T016 单独运行 `mvn test "-Dtest=CustomerPortalIT"` 通过
- [ ] T017 Frontend typecheck / lint / test / build 通过
- [ ] T018 公开端点验证（无需 token 浏览/提单/查进度/不匹配 404）
- [ ] T019 更新契约文档（按实现校正）与 roadmap 050 标记 `[x]`

**Checkpoint**: 模块完整可用

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1**: 依赖 015 知识库
- **Phase 2**: 依赖 Phase 1 + 015 工单 + 005 联系人
- **Phase 3**: 依赖全部完成

## Notes

- 门户公开只读；提单未匹配客户 → 422
- 查进度双验证（工单号+手机/邮箱）防枚举
- 提交规范：Conventional Commits
