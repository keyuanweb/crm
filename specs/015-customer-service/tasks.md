# Tasks: 客户服务模块

**Input**: Design documents from `/specs/015-customer-service/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2/US3

---

## Phase 1: 数据模型与基础

- [x] T001 [P] 创建 Flyway 迁移 V34 in `backend/src/main/resources/db/migration/V34__ticket.sql`（ticket 表 + 索引：idx_ticket_status/assignee/customer）
- [x] T002 [P] 创建 Flyway 迁移 V35 in `backend/src/main/resources/db/migration/V35__ticket_reply.sql`（ticket_reply 表 + idx_ticket_reply_ticket）
- [x] T003 [P] 创建 Flyway 迁移 V36 in `backend/src/main/resources/db/migration/V36__knowledge_article.sql`（knowledge_article 表 + idx_article_status/category）
- [x] T004 [P] 创建 Flyway 迁移 V37 in `backend/src/main/resources/db/migration/V37__sla_policy.sql`（sla_policy 表，priority 唯一）
- [x] T005 [P] H2 测试 schema 同步（ticket/ticket_reply/knowledge_article/sla_policy 表 + 索引）in `backend/src/test/resources/schema-h2.sql`
- [x] T006 [P] ErrorCode 新增 TICKET_*/ARTICLE_*/SLA_* 错误码 in `backend/src/main/java/com/crm/common/ErrorCode.java`
- [x] T007 [P] 创建 Ticket/TicketReply/KnowledgeArticle/SlaPolicy 实体 + 4 个 Mapper in `backend/src/main/java/com/crm/entity/` + `backend/src/main/java/com/crm/repository/`

**Checkpoint**: 数据模型就绪

---

## Phase 2: 用户故事 1 - 工单管理 (P0) 🎯 MVP

**Goal**: 工单 CRUD + 状态流转 + 回复时间线 + 分配 + 分页筛选。

**Independent Test**: 建工单→列表→分配→回复→流转→关闭。

### 实现

- [x] T008 [P] [US1] 创建 TicketRequest/TicketResponse/TicketReplyRequest/TicketReplyResponse DTO in `backend/src/main/java/com/crm/dto/ticket/`
- [x] T009 [US1] 创建 TicketService in `backend/src/main/java/com/crm/service/TicketService.java`（CRUD/状态流转/回复/分配/行级过滤（SALES 仅自身客户）/审计）
- [x] T010 [US1] 创建 TicketController in `backend/src/main/java/com/crm/controller/TicketController.java`（GET 列表/POST/PUT/DELETE、assign/reply/transition；写 ADMIN+SUPPORT）
- [x] T011 [US1] 创建 TicketServiceTest 单元测试 in `backend/src/test/java/com/crm/service/TicketServiceTest.java`（CRUD/状态流转非法 409/回复/分配/SALES 行级过滤）
- [x] T012 [US1] 创建 CustomerServiceIT 集成测试（工单部分）in `backend/src/test/java/com/crm/integration/CustomerServiceIT.java`
- [x] T013 [US1] 前端类型/服务 in `frontend/src/types/ticket.ts` + `frontend/src/services/ticketService.ts`
- [x] T014 [US1] 工单列表页 in `frontend/src/pages/tickets/TicketListPage.tsx`（ProTable + 筛选/创建/分配/流转）
- [x] T015 [US1] 工单详情页 in `frontend/src/pages/tickets/TicketDetailPage.tsx`（信息 + 回复时间线 + SLA 状态）

**Checkpoint**: US1 可用——工单管理

---

## Phase 3: 用户故事 2 - 知识库 (P1)

**Goal**: 知识库文章 CRUD + 发布/下线 + 关键字搜索（仅已发布）。

**Independent Test**: 建文章→发布→搜索命中→下线后不再命中。

### 实现

- [x] T016 [P] [US2] 创建 ArticleRequest/ArticleResponse DTO in `backend/src/main/java/com/crm/dto/knowledge/`
- [x] T017 [US2] 创建 KnowledgeArticleService in `backend/src/main/java/com/crm/service/KnowledgeArticleService.java`（CRUD/发布/下线/搜索/审计）
- [x] T018 [US2] 创建 KnowledgeArticleController in `backend/src/main/java/com/crm/controller/KnowledgeArticleController.java`（GET 列表/POST/PUT/DELETE、publish/unpublish；写 ADMIN+SUPPORT）
- [x] T019 [US2] 创建 KnowledgeArticleServiceTest 单元测试 in `backend/src/test/java/com/crm/service/KnowledgeArticleServiceTest.java`（CRUD/发布状态/搜索仅已发布）
- [x] T020 [US2] CustomerServiceIT 增加知识库用例 in `backend/src/test/java/com/crm/integration/CustomerServiceIT.java`
- [x] T021 [US2] 前端类型/服务 in `frontend/src/types/knowledge.ts` + `frontend/src/services/knowledgeService.ts`
- [x] T022 [US2] 知识库列表页 in `frontend/src/pages/knowledge/KnowledgeArticleListPage.tsx`（列表/创建/编辑/发布/搜索）

**Checkpoint**: US2 可用——知识库

---

## Phase 4: 用户故事 3 - SLA 管理 (P1)

**Goal**: SLA 策略配置 + 工单 SLA 到期时间计算 + 超时标记 + 超时统计。

**Independent Test**: 配策略→建工单→SLA 到期时间正确→超时标记正确。

### 实现

- [x] T023 [P] [US3] 创建 SlaPolicyRequest/SlaPolicyResponse/SlaOverviewResponse DTO in `backend/src/main/java/com/crm/dto/sla/`
- [x] T024 [US3] 创建 SlaPolicyService in `backend/src/main/java/com/crm/service/SlaPolicyService.java`（CRUD/优先级唯一/审计）
- [x] T025 [US3] TicketService 增加 SLA 计算（创建/流转时按策略算到期时间、slaStatus 按需刷新、超时统计）in `backend/src/main/java/com/crm/service/TicketService.java`
- [x] T026 [US3] 创建 SlaPolicyController in `backend/src/main/java/com/crm/controller/SlaPolicyController.java`（/sla-policies CRUD + /tickets/sla-overview；仅 ADMIN）
- [x] T027 [US3] 创建 SlaPolicyServiceTest 单元测试 in `backend/src/test/java/com/crm/service/SlaPolicyServiceTest.java`（CRUD/优先级唯一 409/校验）
- [x] T028 [US3] TicketServiceTest 增加 SLA 计算/超时标记用例 in `backend/src/test/java/com/crm/service/TicketServiceTest.java`
- [x] T029 [US3] CustomerServiceIT 增加 SLA 用例 in `backend/src/test/java/com/crm/integration/CustomerServiceIT.java`
- [x] T030 [US3] 前端类型/服务 in `frontend/src/types/sla.ts` + `frontend/src/services/slaService.ts`
- [x] T031 [US3] SLA 策略配置页 in `frontend/src/pages/sla/SlaPolicyListPage.tsx`（策略 CRUD/启用）

**Checkpoint**: US3 可用——SLA 管理

---

## Phase 5: 收尾与验证

- [x] T032 前端路由与菜单 in `frontend/src/App.tsx`（客户服务菜单 + 工单/知识库/SLA 路由；SLA 仅 ADMIN 可见）
- [x] T033 Backend `mvn verify` 通过（含新增测试、spotless、JaCoCo）
- [x] T034 单独运行 `mvn test "-Dtest=CustomerServiceIT"` 通过
- [x] T035 Frontend typecheck / lint / test / build 通过
- [x] T036 线上端点验证（工单 CRUD→分配→回复→流转→SLA→知识库→超时统计→权限 403）
- [x] T037 更新契约文档（按实现校正）与 roadmap 015 标记 `[x]`

**Checkpoint**: 模块完整可用

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1**: 无依赖，可并行
- **Phase 2 (US1)**: 依赖 Phase 1 完成
- **Phase 3 (US2)**: 依赖 Phase 1 完成；与 US1 无强依赖
- **Phase 4 (US3)**: 依赖 Phase 1 完成；SLA 计算需集成到 TicketService（依赖 US1 的 Ticket 实体）
- **Phase 5**: 依赖全部用户故事完成

### Parallel Opportunities

- Phase 1 的 T001~T007 全部 [P] 可并行
- US1 后端（T008~T012）与 US2/US3 独立实体任务可并行
- 同一故事内 DTO/测试（[P]）可并行

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1 数据模型
2. 完成 Phase 3: US1 工单管理（CRUD/流转/回复/分配）
3. **STOP and VALIDATE**: 工单可独立使用
4. 继续 US2 知识库、US3 SLA

### 增量交付

1. Phase 1 → 数据模型就绪
2. US1 工单管理 → 独立验证（MVP）
3. US2 知识库 → 独立验证
4. US3 SLA（策略 + 工单 SLA 集成）→ 独立验证
5. Phase 5 收尾：路由/菜单/全量验证/文档

## Notes

- 工单回复是独立表（时间线），详情页一次查询分页返回
- SLA 到期时间在创建时落库；slaStatus 读取时按需刷新（WARNING 阈值：剩余 ≤ min(25% 总时限, 2h)）
- SALES 行级过滤：Service 层按 customer.owner_id = 当前用户 校验（012 数据权限惯例）
- 提交规范：每个逻辑组提交一次（Conventional Commits）
