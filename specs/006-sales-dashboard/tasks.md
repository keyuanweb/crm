# Tasks: 销售仪表盘模块

**Input**: Design documents from `/specs/006-sales-dashboard/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2/US3/US4

---

## Phase 1: 数据模型

- [x] T001 [P] 创建 Flyway 迁移 V11 in `backend/src/main/resources/db/migration/V11__sales_target.sql`（sales_target 表：target_month 唯一 + active_key 生成列唯一索引 + 逻辑删除/乐观锁）
- [x] T002 [P] 创建 SalesTarget 实体 in `backend/src/main/java/com/crm/entity/SalesTarget.java`
- [x] T003 [P] 创建 SalesTargetMapper in `backend/src/main/java/com/crm/repository/SalesTargetMapper.java`
- [x] T004 [P] H2 测试 schema 同步 sales_target 表 in `backend/src/test/resources/schema-h2.sql`

**Checkpoint**: 数据模型就绪

---

## Phase 2: 用户故事 1 - 业绩总览与销售漏斗 (P0) 🎯 MVP

**Goal**: 仪表盘聚合接口返回核心指标与漏斗数据；前端一屏展示指标卡与漏斗表。

**Independent Test**: 造数后 `/stats/dashboard` 返回 summary 与 funnel 段；前端卡片数值与列表页一致。

### 实现

- [x] T005 [P] [US1] 创建 DashboardStats 聚合 DTO in `backend/src/main/java/com/crm/dto/stats/DashboardStats.java`（summary/funnel/forecast/performance/followUps/stalled 全字段，US1 先实现 summary+funnel 其余按故事增量填充）
- [x] T006 [US1] 创建 DashboardStatsService in `backend/src/main/java/com/crm/service/DashboardStatsService.java`：summary（商机总数/金额合计/赢单率/客户数/本月新增）+ funnel（阶段数量/金额/相邻转化率）+ Redis 缓存 5 分钟 + evict()
- [x] T007 [US1] StatsController 新增 GET `/api/v1/stats/dashboard` in `backend/src/main/java/com/crm/controller/StatsController.java`（权限 ADMIN+SALES）
- [x] T008 [US1] 创建 DashboardStatsServiceTest 单元测试 in `backend/src/test/java/com/crm/service/DashboardStatsServiceTest.java`（summary/funnel/转化率/缓存）
- [x] T009 [US1] 创建 DashboardStatsIT 集成测试 in `backend/src/test/java/com/crm/integration/DashboardStatsIT.java`（登录→dashboard 端点→summary/funnel 断言）
- [x] T010 [US1] 扩展前端统计类型 in `frontend/src/types/stats.ts`（DashboardStats/Summary/FunnelStage）
- [x] T011 [US1] 扩展前端统计服务 in `frontend/src/services/statsService.ts`（fetchDashboardStats）
- [x] T012 [US1] 创建仪表盘页 in `frontend/src/pages/stats/DashboardPage.tsx`（指标卡 4 张 + 漏斗表格 + 生成时间）
- [x] T013 [US1] App.tsx 将 `/stats` 路由指向 DashboardPage in `frontend/src/App.tsx`（移除旧 OpportunityPipelinePage 引用，删除 `frontend/src/pages/stats/OpportunityPipelinePage.tsx`）

**Checkpoint**: US1 可用——仪表盘首页指标卡+漏斗正确

---

## Phase 3: 用户故事 2 - 销售预测与业绩达成 (P0)

**Goal**: 月度目标设置（管理员）+ 预测加权总额 + 达成率卡片。

**Independent Test**: 管理员 PUT 目标→GET 目标幂等；dashboard.performance 达成率 = 赢单金额/目标；预测 = Σ(金额×概率)。

### 实现

- [x] T014 [P] [US2] 创建 SalesTargetRequest/Response DTO in `backend/src/main/java/com/crm/dto/stats/`（month YYYY-MM 校验、targetAmount ≥0）
- [x] T015 [US2] 创建 SalesTargetService in `backend/src/main/java/com/crm/service/SalesTargetService.java`（按月 upsert：存在则更新否则插入；审计记录；权限校验放 Controller）
- [x] T016 [US2] StatsController 新增 GET/PUT `/api/v1/stats/sales-targets` in `backend/src/main/java/com/crm/controller/StatsController.java`（GET: ADMIN+SALES；PUT: 仅 ADMIN）
- [x] T017 [US2] DashboardStatsService 增加 forecast（Σ 金额×概率 20/50/100/0）与 performance（当月目标 vs CLOSED_WON 金额，按 closed_at 落月）聚合 in `backend/src/main/java/com/crm/service/DashboardStatsService.java`
- [x] T018 [US2] 创建 SalesTargetServiceTest 单元测试 in `backend/src/test/java/com/crm/service/SalesTargetServiceTest.java`（upsert/审计/金额校验）
- [x] T019 [US2] 创建 SalesTargetIT 集成测试 in `backend/src/test/java/com/crm/integration/SalesTargetIT.java`（PUT 幂等/GET/非 ADMIN 403/格式 400）
- [x] T020 [US2] 前端增加目标设置弹窗 + 预测/达成卡片 in `frontend/src/pages/stats/DashboardPage.tsx`（管理员可见设置按钮；未设目标显示引导；fetchSalesTarget/saveSalesTarget 加入 `frontend/src/services/statsService.ts`，类型加入 `frontend/src/types/stats.ts`）

**Checkpoint**: US2 可用——目标可设、达成率与预测正确

---

## Phase 4: 用户故事 3 - 客户与跟进分析 (P1)

**Goal**: 客户分析卡片（总数/活跃/本月新增）+ 跟进报表（方式分布/最近记录）。

**Independent Test**: 造客户与跟进数据→dashboard 客户/跟进段数值正确；无数据时空态。

### 实现

- [x] T021 [US3] DashboardStatsService 增加 customer（总数/ACTIVE 数/本月新增）与 followUps（总数/按 method 分组/最近 10 条含客户名与跟进人）聚合 in `backend/src/main/java/com/crm/service/DashboardStatsService.java`（批量装配客户名/用户名，无 N+1）
- [x] T022 [US3] DashboardStatsServiceTest 增加客户/跟进断言 in `backend/src/test/java/com/crm/service/DashboardStatsServiceTest.java`
- [x] T023 [US3] 前端 DashboardPage 增加客户分析卡片 + 跟进报表卡片（方式分布 Progress + 最近记录表格）in `frontend/src/pages/stats/DashboardPage.tsx`

**Checkpoint**: US3 可用——客户与跟进分析正确

---

## Phase 5: 用户故事 4 - 停滞商机预警 (P1)

**Goal**: 超过 N 天（默认 7，可配置）未更新的活跃销售机会预警列表。

**Independent Test**: 造超期活跃机会→预警列表按停滞天数倒序；刚更新不出现。

### 实现

- [x] T024 [US4] DashboardStatsService 增加 stalledOpportunities 聚合（stage IN 活跃 + updated_at < now-N天，N 默认 7 可配置 `crm.stats.stalled-days`；按停滞天数倒序；批量装配客户名）in `backend/src/main/java/com/crm/service/DashboardStatsService.java`
- [x] T025 [US4] DashboardStatsServiceTest 增加停滞预警断言（含阈值边界）in `backend/src/test/java/com/crm/service/DashboardStatsServiceTest.java`
- [x] T026 [US4] 前端 DashboardPage 增加停滞预警表格（商机/客户/金额/阶段/停滞天数/最后更新，空态）in `frontend/src/pages/stats/DashboardPage.tsx`

**Checkpoint**: US4 可用——停滞预警正确

---

## Phase 6: 验证与收尾

- [x] T027 Backend `mvn verify` 通过（含新增测试、spotless、JaCoCo）
- [x] T028 单独运行 `mvn test -Dtest=DashboardStatsIT,SalesTargetIT` 通过（与既有 *IT 约定一致，不进 verify 默认）
- [x] T029 Frontend typecheck / lint / test / build 通过
- [x] T030 线上端点验证（登录→设目标→查目标→dashboard 六段→非 ADMIN 403）
- [x] T031 更新契约文档 in `specs/006-sales-dashboard/contracts/stats.md`（按实现校正）与 roadmap 006 标记 `[x]`

**Checkpoint**: 模块完整可用
