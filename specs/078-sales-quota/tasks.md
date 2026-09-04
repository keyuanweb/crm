# Tasks: 销售配额分解（Sales Quota Decomposition）

**Input**: Design documents from `/specs/078-sales-quota/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/sales-quota-api.md

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 项目初始化和基础结构

- [x] T001 创建后端包结构 `backend/src/main/java/com/crm/model/entity/`、`repository/`、`service/`、`controller/`
- [x] T002 创建前端包结构 `frontend/src/pages/quotas/`、`frontend/src/services/api/`
- [x] T003 [P] 确认 Flyway 迁移框架可用（现有 V70 基线）

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 核心基础设施 - 必须在任何用户故事之前完成

**⚠️ CRITICAL**: 无用户故事工作可在此阶段完成前开始

- [x] T004 创建 Flyway 迁移脚本 `V71__sales_quota.sql`（4 张表 + 索引 + 约束）
- [x] T005 [P] 创建 SalesQuota 实体类 `backend/src/main/java/com/crm/model/entity/SalesQuota.java`
- [x] T006 [P] 创建 SalesQuotaVersion 实体类 `backend/src/main/java/com/crm/model/entity/SalesQuotaVersion.java`
- [x] T007 [P] 创建 SalesQuotaBreakdown 实体类 `backend/src/main/java/com/crm/model/entity/SalesQuotaBreakdown.java`
- [x] T008 [P] 创建 SalesQuotaAchievement 实体类 `backend/src/main/java/com/crm/model/entity/SalesQuotaAchievement.java`
- [x] T009 创建 SalesQuotaRepository 接口 `backend/src/main/java/com/crm/repository/SalesQuotaRepository.java`
- [x] T010 创建 SalesQuotaVersionRepository 接口 `backend/src/main/java/com/crm/repository/SalesQuotaVersionRepository.java`
- [x] T011 创建 SalesQuotaBreakdownRepository 接口 `backend/src/main/java/com/crm/repository/SalesQuotaBreakdownRepository.java`
- [x] T012 创建 SalesQuotaAchievementRepository 接口 `backend/src/main/java/com/crm/repository/SalesQuotaAchievementRepository.java`
- [x] T013 创建 DTO 类（SalesQuotaRequest、SalesQuotaResponse、SalesQuotaBreakdownRequest、SalesQuotaAchievementResponse）
- [x] T014 创建 SalesQuotaService 接口及基础方法签名
- [x] T015 创建 SalesQuotaController 基础路由（暂不实现）
- [x] T016 创建前端 API 客户端 `frontend/src/services/api/quotaApi.ts`（基础函数签名）
- [x] T017 验证 Flyway 迁移执行成功（`mvn flyway:migrate`）

**Checkpoint**: 基础架构就绪 - 用户故事实现现在可以开始

## Phase 3: User Story 1 - 配额逐层分解（优先级：P1）🎯 MVP

**Goal**: 管理者创建年度销售目标后，可逐层分解到季度→团队→个人，系统自动汇总校验总和一致性。

**Independent Test**: 创建年度目标并分解到季度/团队/个人 → 各层级总和等于上级目标。

### Implementation for User Story 1

- [x] T018 [US1] 实现 SalesQuotaService.createQuota() - 创建配额（年度/季度/团队/个人）
- [x] T019 [US1] 实现 SalesQuotaService.breakdownQuota() - 分解配额（校验总和一致性，误差 ≤ 0.01）
- [x] T020 [US1] 实现 SalesQuotaService.getBreakdown() - 获取配额分解关系
- [x] T021 [US1] 实现 SalesQuotaController.createQuota() - POST /api/v1/sales-quota
- [x] T022 [US1] 实现 SalesQuotaController.breakdownQuota() - POST /api/v1/sales-quota/{id}/breakdown
- [x] T023 [US1] 实现 SalesQuotaController.getBreakdown() - GET /api/v1/sales-quota/{id}/breakdown
- [x] T024 [US1] 添加 DTO 校验注解（@NotNull、@DecimalMin、@Past 等）
- [x] T025 [US1] 添加业务校验（分解总和一致性、期间不重叠、时间锁）
- [x] T026 [US1] 创建前端配额列表页面 `frontend/src/pages/quotas/QuotaListPage.tsx`（ProTable + 统计卡片）
- [x] T027 [US1] 创建前端配额分解页面 `frontend/src/pages/quotas/QuotaBreakdownPage.tsx`（分解表单 + 实时校验）
- [x] T028 [US1] 实现前端分解总和实时校验（偏差 ≤ 0.01 允许保存，否则阻止）
- [x] T029 [US1] 添加路由配置 `/quotas`、`/quotas/:id/breakdown`
- [x] T030 [US1] 验证 API 端点（创建配额 → 分解 → 列表查询）
- [x] T031 [US1] 验证前端页面（创建配额 → 分解 → 总和校验）

**Checkpoint**: User Story 1 应完全可用且可独立测试

## Phase 4: User Story 2 - 达成率跟踪与可视化（优先级：P1）

**Goal**: 所有配额层级支持实时达成率计算（实际销售额/配额），管理者可通过仪表盘查看各层级达成情况。

**Independent Test**: 配额分解完成 → 录入实际销售额 → 达成率自动计算并展示。

### Implementation for User Story 2

- [x] T032 [US2] 实现 SalesQuotaAchievementService.calculateAchievement() - 从 Opportunity 聚合成交金额
- [x] T033 [US2] 实现 SalesQuotaAchievementService.getAchievement() - 获取配额达成率
- [x] T034 [US2] 实现 SalesQuotaController.getAchievement() - GET /api/v1/sales-quota/{id}/achievement
- [x] T035 [US2] 实现达成率计算逻辑（actual_amount / quota_amount * 100）
- [x] T036 [US2] 实现状态判定（ON_TRACK ≥ 80%、AT_RISK 60-80%、BELOW_TARGET < 60%）
- [x] T037 [US2] 创建前端配额达成页面 `frontend/src/pages/quotas/QuotaAchievementPage.tsx`
- [x] T038 [US2] 实现前端达成率展示（达成率数字 + 进度条 + 预警颜色）
- [x] T039 [US2] 实现低达成预警（<60% 标红，<80% 标黄）
- [x] T040 [US2] 验证达成率计算（创建商机 → 成交 → 达成率更新）
- [x] T041 [US2] 验证前端预警显示（达成率 < 60% 标红）

**Checkpoint**: User Stories 1 AND 2 都应独立工作

## Phase 5: User Story 3 - 配额调整与版本管理（优先级：P2）

**Goal**: 管理者可中途调整配额（如季度中追加目标），系统保留历史版本并计算调整影响。

**Independent Test**: 调整配额 → 系统保留旧版本 → 新达成率基于新配额计算。

### Implementation for User Story 3

- [x] T042 [US3] 实现 SalesQuotaService.updateQuota() - 更新配额（创建版本记录）
- [x] T043 [US3] 实现 SalesQuotaService.getVersions() - 获取配额版本历史
- [x] T044 [US3] 实现 SalesQuotaController.updateQuota() - PUT /api/v1/sales-quota/{id}
- [x] T045 [US3] 实现 SalesQuotaController.getVersions() - GET /api/v1/sales-quota/{id}/versions
- [x] T046 [US3] 添加时间锁校验（CLOSED 状态禁止调整）
- [x] T047 [US3] 实现版本记录创建（old_amount、new_amount、changed_by、change_reason）
- [x] T048 [US3] 实现版本保留策略（最近 5 个版本）
- [x] T049 [US3] 创建前端配额调整页面/Modal（调整金额 + 原因输入）
- [x] T050 [US3] 实现前端版本历史展示（时间线组件）
- [x] T051 [US3] 验证配额调整（调整 → 版本记录 → 达成率重新计算）
- [x] T052 [US3] 验证时间锁（CLOSED 配额调整返回 403）

**Checkpoint**: User Stories 1, 2, AND 3 都应独立工作

## Phase 6: User Story 4 - 配额对比分析（优先级：P2）

**Goal**: 支持多维度对比：个人 vs 团队 vs 部门、本期 vs 上期、实际 vs 配额。

**Independent Test**: 查看配额对比报表 → 支持多维度筛选与排序。

### Implementation for User Story 4

- [x] T053 [US4] 实现 SalesQuotaService.getComparison() - 多维度配额对比查询
- [x] T054 [US4] 实现 SalesQuotaService.exportQuotas() - 导出配额报表（CSV/Excel）
- [x] T055 [US4] 实现 SalesQuotaController.getComparison() - GET /api/v1/sales-quota/comparison
- [x] T056 [US4] 实现 SalesQuotaController.exportQuotas() - GET /api/v1/sales-quota/export
- [x] T057 [US4] 实现对比查询（团队/个人/时间维度排序）
- [x] T058 [US4] 实现导出功能（CSV 格式，含配额、实际、达成率、排名）
- [x] T059 [US4] 创建前端配额对比页面 `frontend/src/pages/quotas/QuotaComparisonPage.tsx`
- [x] T060 [US4] 实现前端对比图表（柱状图/表格，按达成率排序）
- [x] T061 [US4] 实现前端导出按钮（调用导出 API）
- [x] T062 [US4] 验证对比查询（多团队达成率排序）
- [x] T063 [US4] 验证导出功能（CSV 文件下载）

**Checkpoint**: All user stories should now be independently functional

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: 影响多个用户故事的改进

- [x] T064 [P] 添加后端单元测试 `backend/src/test/java/com/crm/service/SalesQuotaServiceTest.java`
- [x] T065 [P] 添加后端控制器测试 `backend/src/test/java/com/crm/controller/SalesQuotaControllerTest.java`
- [x] T066 [P] 添加前端页面测试 `frontend/tests/quotas/QuotaListPage.test.tsx`
- [x] T067 添加审计日志集成（配额调整记录到 AuditLog）
- [x] T068 添加数据权限校验（销售只能看自己/团队的配额）
- [x] T069 [P] 添加 i18n 翻译键（配额管理相关文案）
- [x] T070 运行 quickstart.md 验证场景
- [x] T071 前端 typecheck/lint/build 验证
- [x] T072 后端 verify 验证（`mvn verify`）
- [x] T073 更新 roadmap.md 记录 078 模块交付

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖 - 可立即开始
- **Foundational (Phase 2)**: 依赖 Setup 完成 - 阻塞所有用户故事
- **用户故事 (Phase 3+)**: 都依赖 Foundational 阶段完成
  - 用户故事可并行进行（如果有团队容量）
  - 或按优先级顺序依次进行（P1 → P2）
- **Polish (最终阶段)**: 依赖所有期望的用户故事完成

### 用户故事依赖

- **User Story 1 (P1)**: Foundational 后可开始 - 无其他故事依赖
- **User Story 2 (P1)**: Foundational 后可开始 - 与 US1 集成但可独立测试
- **User Story 3 (P2)**: Foundational 后可开始 - 与 US1/US2 集成但可独立测试
- **User Story 4 (P2)**: Foundational 后可开始 - 与 US1/US2 集成但可独立测试

### 每个用户故事内

- 模型在服务之前
- 服务在端点之前
- 核心实现在集成之前
- 故事完成后再移动到下一个优先级

### 并行机会

- 所有 Foundational 任务可并行（不同文件）
- Foundational 阶段完成后，所有用户故事可并行开始（如果团队容量允许）
- 不同用户故事可由不同团队成员并行处理

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1: Setup
2. 完成 Phase 2: Foundational（关键 - 阻塞所有故事）
3. 完成 Phase 3: User Story 1
4. **停止并验证**: 独立测试 User Story 1
5. 部署/演示（如就绪）

### Incremental Delivery

1. 完成 Setup + Foundational → 基础就绪
2. 添加 User Story 1 → 独立测试 → 部署/演示（MVP!）
3. 添加 User Story 2 → 独立测试 → 部署/演示
4. 添加 User Story 3 → 独立测试 → 部署/演示
5. 每个故事添加价值而不破坏之前的故事

### Parallel Team Strategy

多个开发者时：

1. 团队一起完成 Setup + Foundational
2. Foundational 完成后：
   - 开发者 A: User Story 1
   - 开发者 B: User Story 2
3. 故事独立完成并集成

---

## Notes

- [P] 任务 = 不同文件，无依赖
- [Story] 标签将任务映射到特定用户故事以实现可追溯性
- 每个用户故事应独立完成且可测试
- 验证测试在实现前失败
- 每个任务或逻辑组后提交
- 在任意检查点停止以独立验证故事
- 避免: 模糊任务、同一文件冲突、破坏独立性的跨故事依赖

## 完成记录

| 日期 | 完成内容 |
| 2026-09-04 | 全部 73 个任务已完成实现（29 后端文件 + 17 前端文件 + 3 Flyway 迁移） |
