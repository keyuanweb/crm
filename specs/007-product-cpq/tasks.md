# Tasks: 产品与报价模块

**Input**: Design documents from `/specs/007-product-cpq/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2/US3/US4

---

## Phase 1: 数据模型与基础

- [x] T001 [P] 添加 OpenPDF 依赖与字体资源 in `backend/pom.xml`（openpdf 1.3.30）+ `backend/src/main/resources/fonts/wqy-microhei.ttc`
- [x] T002 [P] 创建 Flyway 迁移 V12 in `backend/src/main/resources/db/migration/V12__product.sql`（product 表 + active_key 编码唯一）
- [x] T003 [P] 创建 Flyway 迁移 V13 in `backend/src/main/resources/db/migration/V13__quote.sql`（quote 表 + active_key 单号唯一）
- [x] T004 [P] 创建 Flyway 迁移 V14 in `backend/src/main/resources/db/migration/V14__quote_item.sql`（quote_item 表）
- [x] T005 [P] 创建 Product 实体/Mapper in `backend/src/main/java/com/crm/entity/Product.java` + `backend/src/main/java/com/crm/repository/ProductMapper.java`
- [x] T006 [P] 创建 Quote 实体/Mapper in `backend/src/main/java/com/crm/entity/Quote.java` + `backend/src/main/java/com/crm/repository/QuoteMapper.java`
- [x] T007 [P] 创建 QuoteItem 实体/Mapper in `backend/src/main/java/com/crm/entity/QuoteItem.java` + `backend/src/main/java/com/crm/repository/QuoteItemMapper.java`
- [x] T008 [P] H2 测试 schema 同步三表 in `backend/src/test/resources/schema-h2.sql`
- [x] T009 [P] ErrorCode 新增 PRODUCT_DUPLICATE/PRODUCT_NOT_FOUND/QUOTE_NOT_FOUND/QUOTE_INVALID_STATE/OPPORTUNITY_CUSTOMER_MISMATCH in `backend/src/main/java/com/crm/common/ErrorCode.java`

**Checkpoint**: 数据模型就绪

---

## Phase 2: 用户故事 1 - 产品目录维护 (P0)

**Goal**: 产品 CRUD + 编码唯一 + 停用产品从报价候选排除。

**Independent Test**: 创建产品→列表→编辑→停用后不出现在报价候选。

### 实现

- [x] T010 [P] [US1] 创建 ProductRequest/ProductResponse DTO in `backend/src/main/java/com/crm/dto/product/`
- [x] T011 [US1] 创建 ProductService in `backend/src/main/java/com/crm/service/ProductService.java`（CRUD/编码唯一 409/逻辑删除/审计）
- [x] T012 [US1] 创建 ProductController in `backend/src/main/java/com/crm/controller/ProductController.java`（GET/POST/PUT/DELETE /api/v1/products，写仅 ADMIN）
- [x] T013 [US1] 创建 ProductServiceTest 单元测试 in `backend/src/test/java/com/crm/service/ProductServiceTest.java`（唯一 409/404/逻辑删除）
- [x] T014 [US1] 创建 ProductIT 集成测试 in `backend/src/test/java/com/crm/integration/ProductIT.java`（CRUD/权限 403/唯一 409）
- [x] T015 [US1] 前端类型与服务 in `frontend/src/types/product.ts` + `frontend/src/services/productService.ts`
- [x] T016 [US1] 产品管理页 in `frontend/src/pages/products/ProductListPage.tsx`（ProTable + 新增/编辑/停用弹窗）

**Checkpoint**: US1 可用——产品目录完整

---

## Phase 3: 用户故事 2 - 报价单创建与编辑 (P0)

**Goal**: 报价单创建/编辑（行明细自动算额）+ 草稿/提交。

**Independent Test**: 创建报价（2 行）→ 合计正确 → 编辑重算 → 提交。

### 实现

- [x] T017 [P] [US2] 创建 Quote DTO in `backend/src/main/java/com/crm/dto/quote/`（QuoteRequest/QuoteResponse/QuoteItemRequest/QuoteItemResponse）
- [x] T018 [US2] 创建 QuoteService in `backend/src/main/java/com/crm/service/QuoteService.java`（创建/编辑/行快照与算额/报价单号生成/客户与商机校验）
- [x] T019 [US2] 创建 QuoteController in `backend/src/main/java/com/crm/controller/QuoteController.java`（GET 列表/详情、POST/PUT、POST submit）
- [x] T020 [US2] 创建 QuoteServiceTest 单元测试 in `backend/src/test/java/com/crm/service/QuoteServiceTest.java`（算额/状态机/客户商机不匹配）
- [x] T021 [US2] 创建 QuoteIT 集成测试 in `backend/src/test/java/com/crm/integration/QuoteIT.java`（创建→编辑→提交流程）
- [x] T022 [US2] 前端类型与服务 in `frontend/src/types/quote.ts` + `frontend/src/services/quoteService.ts`
- [x] T023 [US2] 报价单列表页 in `frontend/src/pages/quotes/QuoteListPage.tsx`（ProTable + 创建弹窗含行编辑）
- [x] T024 [US2] 报价单详情页 in `frontend/src/pages/quotes/QuoteDetailPage.tsx`（行明细表 + 提交按钮）

**Checkpoint**: US2 可用——报价创建/编辑/提交完整

---

## Phase 4: 用户故事 3 - 报价审批 (P1)

**Goal**: 管理员审批（通过/拒绝+意见）。

**Independent Test**: 提交→审批通过/拒绝→状态与记录正确。

### 实现

- [x] T025 [US3] QuoteService 增加 approve/reject（状态机校验/审批人与时间记录/拒绝意见必填）in `backend/src/main/java/com/crm/service/QuoteService.java`
- [x] T026 [US3] QuoteController 增加 POST approve/reject 端点（仅 ADMIN）in `backend/src/main/java/com/crm/controller/QuoteController.java`
- [x] T027 [US3] QuoteServiceTest 增加审批状态机用例 in `backend/src/test/java/com/crm/service/QuoteServiceTest.java`
- [x] T028 [US3] QuoteIT 增加审批流程用例 in `backend/src/test/java/com/crm/integration/QuoteIT.java`（通过/拒绝/重复审批 409/非 ADMIN 403）
- [x] T029 [US3] 前端详情页增加审批操作（通过/拒绝弹窗，仅 ADMIN 可见）in `frontend/src/pages/quotes/QuoteDetailPage.tsx`

**Checkpoint**: US3 可用——审批闭环

---

## Phase 5: 用户故事 4 - 报价单 PDF 导出 (P2)

**Goal**: OpenPDF 生成中文 PDF。

**Independent Test**: 导出 PDF 可下载、含关键字段、中文无乱码。

### 实现

- [x] T030 [US4] 创建 QuotePdfService in `backend/src/main/java/com/crm/service/QuotePdfService.java`（OpenPDF + wqy-microhei 字体，生成报价单 PDF：单号/客户/行明细/总额/状态）
- [x] T031 [US4] QuoteController 增加 GET /quotes/{id}/pdf in `backend/src/main/java/com/crm/controller/QuoteController.java`
- [x] T032 [US4] QuoteIT 增加 PDF 导出用例（200 + application/pdf + 内容长度）in `backend/src/test/java/com/crm/integration/QuoteIT.java`
- [x] T033 [US4] 前端详情页增加"导出 PDF"按钮 in `frontend/src/pages/quotes/QuoteDetailPage.tsx`

**Checkpoint**: US4 可用——PDF 导出

---

## Phase 6: 收尾与验证

- [x] T034 前端路由与菜单 in `frontend/src/App.tsx`（产品/报价菜单 + 路由）
- [x] T035 Backend `mvn verify` 通过（含新增测试、spotless、JaCoCo）
- [x] T036 单独运行 `mvn test "-Dtest=ProductIT,QuoteIT"` 通过
- [x] T037 Frontend typecheck / lint / test / build 通过
- [x] T038 线上端点验证（产品 CRUD→报价创建→提交→审批→PDF→权限 403）
- [x] T039 更新契约文档（按实现校正）与 roadmap 007 标记 `[x]`

**Checkpoint**: 模块完整可用
