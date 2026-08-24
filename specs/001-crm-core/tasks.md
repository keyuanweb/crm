# Tasks: CRM 客户关系管理系统（初始版本）

**Input**: Design documents from `/specs/001-crm-core/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: 项目章程（原则四，不可协商）要求测试先于实现与合并门禁，故每个用户故事包含
契约测试、集成测试与单元测试任务（红-绿-重构）。

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Web app**: `backend/src/`、`frontend/src/`（见 plan.md 结构决策：仓库根为 backend/ + frontend/）

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [x] T001 Create backend Maven project skeleton: `backend/pom.xml`（Spring Boot 3.2.0 parent）、`backend/src/main/java/com/crm/CrmApplication.java`
- [x] T002 Create frontend Vite React-TS project skeleton: `frontend/package.json`、`frontend/vite.config.ts`、`frontend/src/main.tsx`、`frontend/index.html`
- [x] T003 [P] Configure backend dependencies in `backend/pom.xml`：Spring Security 6.2、MyBatis-Plus 3.5.5、MySQL Driver、Redis、Validation、Lombok、springdoc-openapi (Swagger 3.0)、Apache POI、Flyway（版本按 research.md R1/R6/R8）
- [x] T004 [P] Configure frontend dependencies in `frontend/package.json`：react-router-dom 6.20、@tanstack/react-query 5、zustand 4.5、tailwindcss 3.4、axios；devDependencies：jest + @testing-library/react、@playwright/test
- [x] T005 [P] Configure lint/format：`frontend/eslint.config.js` + Prettier；`backend/pom.xml` 增加 spotless-maven-plugin（代码风格门禁，章程原则五）
- [x] T006 [P] Set up environment config：`backend/src/main/resources/application.yml` + `application-dev.yml`（MySQL/Redis 连接、JWT 密钥与过期时间走环境变量，绝不硬编码——章程技术与架构约束）、`frontend/.env.development`（VITE_API_BASE_URL=/api/v1，经 Vite 代理至后端 8081）

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T007 Create Flyway migration `backend/src/main/resources/db/migration/V1__init.sql`：user、customer、opportunity、sales_opportunity、follow_up 五张表（字段与索引见 data-model.md，含 deleted/version 列）+ 种子管理员 admin/admin123（bcrypt 哈希，research.md R1）
- [x] T008 Create common envelope & error handling：`backend/src/main/java/com/crm/common/`（PageResult、ApiResponse、ErrorCode 枚举）+ `backend/src/main/java/com/crm/exception/GlobalExceptionHandler.java`（400/401/403/404/409/422 映射，契约 README）
- [x] T009 Implement Spring Security + JWT 认证授权：`backend/src/main/java/com/crm/config/SecurityConfig.java`（无状态 JWT 过滤器链、角色 ADMIN/SALES/SUPPORT、方法级 @PreAuthorize，research.md R1）
- [x] T010 [P] Implement auth service/controller：`backend/src/main/java/com/crm/service/AuthService.java`、`backend/src/main/java/com/crm/controller/AuthController.java`（login/refresh/logout/me，按 contracts/auth.md；refresh token 存 Redis）
- [x] T011 [P] Configure Redis：`backend/src/main/java/com/crm/config/RedisConfig.java`（序列化、缓存管理器，research.md R3）
- [x] T012 [P] Configure MyBatis-Plus：`backend/src/main/java/com/crm/config/MybatisPlusConfig.java`（分页插件、逻辑删除、乐观锁插件，research.md R2/R4）
- [x] T013 Create base entity：`backend/src/main/java/com/crm/entity/BaseEntity.java`（id/createdAt/updatedAt/deleted/version 公共字段）
- [x] T014 [P] Implement structured logging：`backend/src/main/java/com/crm/config/LoggingFilter.java`（SLF4J 请求日志，章程原则五；用户可见错误服务端必记录）
- [x] T015 Create frontend foundation：`frontend/src/services/apiClient.ts`（axios 实例 + Bearer 拦截 + 401 跳登录）、`frontend/src/store/authStore.ts`（Zustand 单一状态管理）、`frontend/src/hooks/`（React Query 封装）
- [x] T016 Create login page & route guard：`frontend/src/pages/LoginPage.tsx`、`frontend/src/App.tsx`（React Router 路由 + 未登录重定向）

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - 客户全生命周期管理 (Priority: P1) 🎯 MVP

**Goal**: 销售/管理员可创建、查看、编辑、删除客户，形成客户主数据（FR-001~005，US1）

**Independent Test**: 登录后即可独立维护客户档案（新增→列表可见→编辑生效→删除消失且可恢复）

### Tests for User Story 1（章程原则四：先写测试，红-绿-重构）⚠️

- [x] T017 [P] [US1] Contract test for customers endpoints（请求/响应结构与契约一致）in `backend/src/test/java/com/crm/contract/CustomerContractTest.java`
- [x] T018 [P] [US1] Integration test for 客户 CRUD 全流程（含逻辑删除、详情聚合商机/跟进）in `backend/src/test/java/com/crm/integration/CustomerIT.java`
- [x] T019 [P] [US1] Unit test for CustomerService（校验、name+company 去重、乐观锁冲突）in `backend/src/test/java/com/crm/service/CustomerServiceTest.java`

### Implementation for User Story 1

- [x] T020 [P] [US1] Create Customer entity + mapper in `backend/src/main/java/com/crm/entity/Customer.java`、`backend/src/main/java/com/crm/repository/CustomerMapper.java`（继承 BaseEntity，@TableLogic/@Version）
- [x] T021 [P] [US1] Create Customer DTOs（请求/响应 + Jakarta Bean Validation 注解）in `backend/src/main/java/com/crm/dto/customer/`（校验规则见 data-model.md §2）
- [x] T022 [US1] Implement CustomerService（CRUD、逻辑删除、乐观锁、事务边界在 Service 层）in `backend/src/main/java/com/crm/service/CustomerService.java`（依赖 T020、T021）
- [x] T023 [US1] Implement CustomerController（列表/详情/新增/编辑/删除，详情聚合商机与跟进）in `backend/src/main/java/com/crm/controller/CustomerController.java`（按 contracts/customers.md）
- [x] T024 [US1] Frontend: customer list/detail/form in `frontend/src/pages/customers/CustomerListPage.tsx`、`CustomerDetailPage.tsx`、`CustomerForm.tsx` + `frontend/src/services/customerService.ts`、`frontend/src/types/customer.ts`
- [x] T025 [US1] Frontend: delete confirm + 乐观锁冲突提示（409 处理）in `frontend/src/pages/customers/`

**Checkpoint**: 至此 US1 应完全可用并可独立验证（S2 场景）

---

## Phase 4: User Story 2 - 客户检索与分页浏览 (Priority: P1)

**Goal**: 关键字搜索 + 状态筛选 + 分页浏览，10 万数据量下 ≤2 秒（FR-001、SC-002，US2）

**Independent Test**: 在批量客户数据上独立验证搜索命中、筛选、翻页保持条件

### Tests for User Story 2 ⚠️

- [x] T026 [P] [US2] Integration test for 搜索/筛选/分页 in `backend/src/test/java/com/crm/integration/CustomerSearchIT.java`
- [x] T027 [P] [US2] Performance smoke test（≥10 万条种子数据下查询 ≤2s）in `backend/src/test/java/com/crm/perf/CustomerSearchPerfIT.java`

### Implementation for User Story 2

- [x] T028 [P] [US2] Backend: keyword + status 筛选与分页实现（LIKE 转义、复合索引，research.md R4）in `backend/src/main/java/com/crm/service/CustomerService.java`、`backend/src/main/resources/db/migration/V2__customer_search_indexes.sql`
- [x] T029 [US2] Frontend: 搜索框 + 筛选 + 分页组件（React Query 保持条件）in `frontend/src/pages/customers/CustomerListPage.tsx`、`frontend/src/components/CustomerFilter.tsx`

**Checkpoint**: US1 + US2 均独立可用（S3 场景）

---

## Phase 5: User Story 3 - 商机管理与管道跟进 (Priority: P2)

**Goal**: 商机（父）CRUD + 销售机会（子）阶段管道与关闭（FR-007~014、US3，Q1 选项 C）

**Independent Test**: 在已有客户数据下独立完成 创建商机→创建销售机会→阶段流转→关闭

### Tests for User Story 3 ⚠️

- [x] T030 [P] [US3] Contract test for opportunities + sales-opportunities endpoints in `backend/src/test/java/com/crm/contract/OpportunityContractTest.java`
- [x] T031 [P] [US3] Integration test for 商机→销售机会→关闭 全流程 in `backend/src/test/java/com/crm/integration/OpportunityIT.java`
- [x] T032 [P] [US3] Unit test for SalesOpportunity 状态机（合法/非法流转、终态禁止编辑）in `backend/src/test/java/com/crm/service/SalesOpportunityStateTest.java`

### Implementation for User Story 3

- [x] T033 [P] [US3] Create Opportunity + SalesOpportunity entities/mappers（父子 FK、stage 枚举）in `backend/src/main/java/com/crm/entity/Opportunity.java`、`backend/src/main/java/com/crm/entity/SalesOpportunity.java`、`backend/src/main/java/com/crm/repository/`
- [x] T034 [P] [US3] Create Opportunity/SalesOpportunity DTOs + 校验（金额范围、stage、closeResult）in `backend/src/main/java/com/crm/dto/opportunity/`
- [x] T035 [US3] Implement OpportunityService + SalesOpportunityService（阶段流转、关闭写入 closeResult/closedAt、级联逻辑删除）in `backend/src/main/java/com/crm/service/`（依赖 T033、T034）
- [x] T036 [US3] Implement OpportunityController + SalesOpportunityController in `backend/src/main/java/com/crm/controller/`（按 contracts/opportunities.md、sales-opportunities.md）
- [x] T037 [US3] Frontend: 商机列表/详情/表单 + 销售机会管道视图（阶段筛选、流转、关闭操作）in `frontend/src/pages/opportunities/`、`frontend/src/pages/sales-opportunities/` + `frontend/src/services/opportunityService.ts`
- [x] T038 [US3] Frontend: 客户详情集成商机列表 in `frontend/src/pages/customers/CustomerDetailPage.tsx`

**Checkpoint**: US1~US3 独立可用（S4 场景）

---

## Phase 6: User Story 4 - 跟进记录管理 (Priority: P2)

**Goal**: 为客户/商机添加、查看、编辑跟进记录，形成可追溯时间线（FR-015、US4）

**Independent Test**: 已有客户/商机数据下独立完成跟进记录增查改与权限校验

### Tests for User Story 4 ⚠️

- [x] T039 [P] [US4] Integration test for 跟进时间线 + 归属/权限规则 in `backend/src/test/java/com/crm/integration/FollowUpIT.java`

### Implementation for User Story 4

- [x] T040 [P] [US4] Create FollowUp entity/mapper + DTOs（method 枚举、opportunity-customer 一致性校验）in `backend/src/main/java/com/crm/entity/FollowUp.java`、`backend/src/main/java/com/crm/dto/followup/`
- [x] T041 [US4] Implement FollowUpService（仅本人或 ADMIN 可编辑，research.md R1 权限矩阵）in `backend/src/main/java/com/crm/service/FollowUpService.java`（依赖 T040）
- [x] T042 [US4] Implement FollowUpController in `backend/src/main/java/com/crm/controller/FollowUpController.java`（按 contracts/follow-ups.md）
- [x] T043 [US4] Frontend: 跟进时间线组件 + 新增/编辑表单 in `frontend/src/components/FollowUpTimeline.tsx`、`frontend/src/pages/customers/CustomerDetailPage.tsx`

**Checkpoint**: US1~US4 独立可用（S5 场景）

---

## Phase 7: User Story 5 - 客户批量导入导出 (Priority: P3)

**Goal**: Excel 导入（校验+错误明细）、导出（当前筛选结果）、模板下载（FR-006、US5，仅 ADMIN）

**Independent Test**: 独立验证模板导入、逐行校验报告、导出文件正确性

### Tests for User Story 5 ⚠️

- [x] T044 [P] [US5] Integration test for 导入（成功/失败明细）+ 导出内容 in `backend/src/test/java/com/crm/integration/CustomerImportExportIT.java`

### Implementation for User Story 5

- [x] T045 [P] [US5] Implement Excel 服务（Apache POI、模板生成、逐行校验、500 行分批事务，research.md R6）in `backend/src/main/java/com/crm/service/CustomerExcelService.java`
- [x] T046 [US5] Implement import/export/template 端点（ADMIN 权限）in `backend/src/main/java/com/crm/controller/CustomerController.java`（按 contracts/customers.md）
- [x] T047 [US5] Frontend: 导入对话框 + 导出/模板下载按钮（仅管理员可见）in `frontend/src/pages/customers/`、`frontend/src/store/authStore.ts`（角色控制）

**Checkpoint**: US1~US5 独立可用（S6 场景）

---

## Phase 8: User Story 6 - 商机统计报表 (Priority: P3)

**Goal**: 按销售机会阶段汇总数量与金额（FR-011、SC-006、US6，ADMIN+SALES）

**Independent Test**: 独立验证各阶段汇总正确性与列表一致性

### Tests for User Story 6 ⚠️

- [x] T048 [P] [US6] Integration test for 管道统计聚合 + 与列表一致性 in `backend/src/test/java/com/crm/integration/StatsIT.java`

### Implementation for User Story 6

- [x] T049 [P] [US6] Implement 统计服务（按 stage 聚合、Redis 缓存 5 分钟、关闭/编辑后失效，research.md R10）in `backend/src/main/java/com/crm/service/OpportunityStatsService.java`
- [x] T050 [US6] Implement StatsController in `backend/src/main/java/com/crm/controller/StatsController.java`（按 contracts/stats.md）
- [x] T051 [US6] Frontend: 统计页（阶段数量/金额汇总展示）in `frontend/src/pages/stats/OpportunityPipelinePage.tsx`、`frontend/src/services/statsService.ts`

**Checkpoint**: 全部 6 个用户故事独立可用（S7 场景）

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [x] T052 [P] Security hardening：登录失败限流/锁定（Redis 计数，research.md R1 遗留项）in `backend/src/main/java/com/crm/config/`
- [x] T053 [P] Backend 覆盖率门禁（JaCoCo）+ CI 工作流 in `backend/pom.xml`、`.github/workflows/ci.yml`（章程原则四：构建/单测/集成/Lint 全绿合并）
- [x] T054 [P] Frontend typecheck/lint/coverage scripts in `frontend/package.json`（`pnpm run typecheck`、`pnpm run lint`、`pnpm run test -- --coverage`）
- [x] T055 Run quickstart.md 验证场景 S1~S7（含 Playwright e2e）并修复差距
- [x] T056 Documentation updates：仓库根 `README.md`（安装/启动/默认账号说明，参考 quickstart.md）
- [x] T057 Code cleanup and refactoring across `backend/`、`frontend/`（章程原则五：简洁、无 N+1 复查）

---

## Phase 10: 系统导航与布局（US7，FR-018~019）

**Goal**: 顶栏固定（滚动可见）+ 左侧菜单按业务域分组（US7 验收场景）

**Independent Test**: 任意页面滚动内容时顶栏保持固定；菜单分组展示且当前页高亮

- [x] T058 [US7] 前端：`frontend/src/App.tsx` 外层 Layout 改为 `height:100vh` + `overflow:hidden`，Header 固定 56px，Content `overflow:auto` 独立滚动（FR-018 顶栏固定）
- [x] T059 [US7] 前端：`frontend/src/App.tsx` 左侧 Menu 改为分组结构——客户管理（线索/客户/联系人）、销售管理（商机/销售机会/报价单）、交易管理（合同/订单）、基础资料（产品/任务）、营销与服务、数据分析、系统管理（仅 ADMIN），当前页高亮（FR-019）
- [x] T060 [US7] 验证：`pnpm run typecheck/lint/test/build` 全绿，刷新验证顶栏固定与菜单分组

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - User stories can then proceed in parallel (if staffed)
  - Or sequentially in priority order (P1 → P2 → P3)
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **US1 (P1)**: 依赖 Foundational；Customer 实体/服务为 US2 复用，无其他故事依赖
- **US2 (P1)**: 依赖 Foundational；复用 US1 的 Customer 模型，但可独立验证
- **US3 (P2)**: 依赖 Foundational + US1（商机必须关联客户）；不依赖 US2
- **US4 (P2)**: 依赖 Foundational + US1（跟进必须关联客户）；商机关联可选
- **US5 (P3)**: 依赖 Foundational + US1（导入数据即客户）；独立验证
- **US6 (P3)**: 依赖 Foundational + US3（统计聚合销售机会）；独立验证

### Within Each User Story

- Tests MUST be written and FAIL before implementation（章程原则四）
- Models before services
- Services before endpoints
- Core implementation before integration
- Story complete before moving to next priority

### Parallel Opportunities

- 所有标记 [P] 的 Setup/Foundational 任务可并行
- Foundational 完成后，各用户故事可按优先级串行或并行（多开发者）
- 每个故事内的 Tests（[P]）与 Models（[P]）可并行
- US2/US4/US5/US6 在数据上仅弱依赖 US1/US3，可交叉并行

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together:
Task: "Contract test for customers endpoints in backend/src/test/java/com/crm/contract/CustomerContractTest.java"
Task: "Integration test for 客户 CRUD 全流程 in backend/src/test/java/com/crm/integration/CustomerIT.java"
Task: "Unit test for CustomerService in backend/src/test/java/com/crm/service/CustomerServiceTest.java"

# Launch all models for User Story 1 together:
Task: "Create Customer entity in backend/src/main/java/com/crm/entity/Customer.java"
Task: "Create Customer DTOs in backend/src/main/java/com/crm/dto/customer/"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational（含认证登录，blocking）
3. Complete Phase 3: User Story 1（客户 CRUD + 登录）
4. **STOP and VALIDATE**: 执行 quickstart S1~S2 验证 US1
5. Deploy/demo if ready（MVP = 登录 + 客户管理）

### Incremental Delivery

1. Setup + Foundational → 基础就绪（可登录）
2. US1 客户管理 → 独立测试 → MVP 演示
3. US2 检索分页 → 独立测试 → 交付
4. US3 商机管道 → 独立测试 → 交付
5. US4 跟进记录 → 独立测试 → 交付
6. US5 导入导出 → 独立测试 → 交付
7. US6 统计报表 → 独立测试 → 交付
8. Phase 9 Polish → 全量回归

### Parallel Team Strategy

- 基础就绪后：开发者 A → US1+US2（客户域）；开发者 B → US3+US6（商机域，等待 US1 客户数据）
- 开发者 C → US4（依赖 US1 的客户详情）；US5 由 A 或独立成员在 US1 后跟进

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- 每个用户故事独立可完成、可测试；测试先失败再实现（章程原则四）
- **Deferred（来自未完成的 /speckit-clarify）**：PII 数据保护（脱敏/审计）问题未决——本清单仅含基线措施
  （T052 登录防护、T053 门禁、T014 日志）；若后续选择"最小必要保护/完整合规管控"，需在 US1 增加
  敏感字段脱敏与操作审计任务，并回看 plan/data-model。
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence

---

## Phase 10: Convergence

**Purpose**: `/speckit-converge` 一致性评估（2026-08-22）追加的剩余工作；完成后可再次 converge 复查

- [x] T058 Run quickstart.md 验证场景 S1~S7（启动 MySQL/Redis + 后端 8081 + 前端）并执行 Playwright e2e per tasks.md T055 / quickstart.md (missing)
- [x] T059 执行 10 万条客户数据规模下的搜索/筛选/分页性能验证，确认感知响应 ≤2 秒 per SC-002 (partial)
- [x] T060 创建 `frontend/src/hooks/` 目录并将 React Query 数据获取封装为 hooks（页面查询迁移） per tasks.md T015 (partial)

---

## Phase 11: PII 数据保护（会话 2026-08-22 澄清 Q1 → 选项 A）

**Purpose**: 最小必要保护——列表脱敏 + 关键操作审计日志 + 导出仅管理员（导出权限已具备）

- [x] T061 [P] Create 脱敏工具（电话/邮箱脱敏规则）in `backend/src/main/java/com/crm/common/MaskingUtil.java`（FR-016）
- [x] T062 [P] Create audit_log 表迁移 in `backend/src/main/resources/db/migration/V3__audit_log.sql` + `backend/src/main/java/com/crm/entity/AuditLog.java`、`repository/AuditLogMapper.java`（FR-017）
- [x] T063 [P] Implement AuditService（记录操作人/类型/对象/时间；普通用户不可写）in `backend/src/main/java/com/crm/service/AuditService.java`（FR-017）
- [x] T064 [US1] 客户列表/搜索响应脱敏（电话/邮箱），详情保持完整值 in `backend/src/main/java/com/crm/service/CustomerService.java`（FR-016）
- [x] T065 [US1] 关键操作写入审计：客户增删改、导入导出、销售机会关闭 in `backend/src/main/java/com/crm/service/`（FR-017）
- [x] T066 [US1] 前端客户列表展示脱敏邮箱列 in `frontend/src/pages/customers/CustomerListPage.tsx`（FR-016）
- [x] T067 审计与脱敏测试（单元 + 集成）+ H2 schema 更新 in `backend/src/test/`（FR-016/FR-017）
- [x] T068 更新文档：`contracts/customers.md`、`data-model.md` 记录脱敏与审计约定（FR-016/FR-017）

---

## Phase 12: Convergence（审计查询入口，FR-017"仅管理员可查"）

**Purpose**: 补齐 FR-017 的读取侧缺口——审计日志查询接口与前端查看页（此前仅实现写入）

- [x] T069 [P] Create 审计日志查询接口 `GET /api/v1/audit-logs`（仅 ADMIN，支持分页与按操作类型/对象类型/操作人筛选）in `backend/src/main/java/com/crm/service/AuditLogService.java`、`backend/src/main/java/com/crm/controller/AuditLogController.java` per FR-017 (partial)
- [x] T070 [P] 审计查询集成测试（管理员可查、非管理员 403、筛选/分页正确）in `backend/src/test/java/com/crm/integration/AuditLogIT.java` per FR-017 (partial)
- [x] T071 前端审计日志查看页（仅管理员可见路由/导航）+ 类型与服务 in `frontend/src/pages/audit/AuditLogPage.tsx`、`frontend/src/services/auditLogService.ts`、`frontend/src/types/auditLog.ts`、`frontend/src/App.tsx` per FR-017 (partial)
