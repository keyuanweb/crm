# Tasks: 托管落地页 + UTM 跟踪模块

**Input**: Design documents from `/specs/053-landing-page/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2

---

## Phase 1: 数据模型与基础

- [x] T001 [P] 创建 Flyway 迁移 V62 in `backend/src/main/resources/db/migration/V62__landing_page.sql`（landing_page 表 + form_submission UTM 列）
- [x] T002 [P] H2 测试 schema 同步 in `backend/src/test/resources/schema-h2.sql`
- [x] T003 [P] 创建 LandingPage 实体 + Mapper；FormSubmission 加 UTM 字段 in `backend/src/main/java/com/crm/entity/` + `backend/src/main/java/com/crm/repository/`
- [x] T004 [P] ErrorCode 新增 LANDING_* 错误码 in `backend/src/main/java/com/crm/common/ErrorCode.java`
- [x] T005 [P] 创建 LandingPageRequest/Response/PublicResponse/UtmStatsResponse DTO in `backend/src/main/java/com/crm/dto/landing/`

**Checkpoint**: 数据模型就绪

---

## Phase 2: 用户故事 1 - 落地页配置与公开访问 (P0)

**Goal**: 落地页 CRUD + 公开渲染（/lp/{id}）。

**Independent Test**: 配置→公开访问→渲染+提交。

### 实现

- [x] T006 [P] [US1] 创建 LandingPageService（CRUD + 公开渲染校验）in `backend/src/main/java/com/crm/service/LandingPageService.java`
- [x] T007 [US1] 创建 LandingPageController（/landing-pages 管理 + /public/lp 渲染）in `backend/src/main/java/com/crm/controller/LandingPageController.java`
- [x] T008 [US1] 创建 LandingPageServiceTest 单元测试 in `backend/src/test/java/com/crm/service/LandingPageServiceTest.java`

**Checkpoint**: US1 可用——落地页配置与渲染

---

## Phase 3: 用户故事 2 - UTM 跟踪与归因 (P0)

**Goal**: 提交捕获 UTM + 归因统计。

**Independent Test**: 带 UTM 提交→快照记录→统计聚合。

### 实现

- [x] T009 [P] [US2] FormService 公开提交端点捕获 UTM 参数 in `backend/src/main/java/com/crm/service/FormService.java`
- [x] T010 [US2] LandingPageService 增加 UTM 统计聚合（按 source/campaign）in `backend/src/main/java/com/crm/service/LandingPageService.java`
- [x] T011 [US2] LandingPageController 增加 /landing-pages/{id}/stats in `backend/src/main/java/com/crm/controller/LandingPageController.java`
- [x] T012 [US2] 创建 LandingPageIT 集成测试 in `backend/src/test/java/com/crm/integration/LandingPageIT.java`
- [x] T013 [US2] 前端类型/服务 in `frontend/src/types/landingPage.ts` + `frontend/src/services/landingPageService.ts`
- [x] T014 [US2] 落地页配置页 in `frontend/src/pages/landing/LandingPageListPage.tsx`（CRUD + 统计）
- [x] T015 [US2] 公开落地页渲染页 in `frontend/src/pages/landing/LandingPageView.tsx`（/lp/:id）

**Checkpoint**: US2 可用——UTM 跟踪

---

## Phase 4: 收尾与验证

- [x] T016 App.tsx 点亮"落地页"占位项为路由（/landing-pages）+ 公开路由 /lp/:id in `frontend/src/App.tsx`
- [x] T017 Backend `mvn verify` 通过（含新增测试、spotless、JaCoCo）
- [x] T018 单独运行 `mvn test "-Dtest=LandingPageIT"` 通过
- [x] T019 Frontend typecheck / lint / test / build 通过
- [x] T020 线上端点验证（配置→公开渲染→UTM 提交→统计）
- [x] T021 更新契约文档（按实现校正）与 roadmap 053 标记 `[x]`

**Checkpoint**: 模块完整可用

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1**: 依赖 036 表单
- **Phase 2 (US1)**: 依赖 Phase 1
- **Phase 3 (US2)**: 依赖 Phase 1/2
- **Phase 4**: 依赖全部完成

## Notes

- UTM 从公开提交 URL 查询字符串解析
- 统计为提交数聚合（v1）
- 提交规范：Conventional Commits
