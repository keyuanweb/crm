# Tasks: 数据保留策略（Data Retention Policy）

**Input**: Design documents from `/specs/080-data-retention/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/data-retention-api.md

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 项目初始化和基础结构

- [x] T001 创建后端包结构 `backend/src/main/java/com/crm/model/entity/`、`repository/`、`service/`、`controller/`
- [x] T002 创建前端包结构 `frontend/src/pages/settings/`、`frontend/src/services/api/`
- [x] T003 [P] 确认 Spring Schedule 框架可用（Spring Boot 内置）

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 核心基础设施 - 必须在任何用户故事之前完成

**⚠️ CRITICAL**: 无用户故事工作可在此阶段完成前开始

- [x] T004 创建 Flyway 迁移脚本 `V74__data_retention.sql`（3 张表 + 索引 + 约束）
- [x] T005 [P] 创建 DataRetentionPolicy 实体类 `backend/src/main/java/com/crm/model/entity/DataRetentionPolicy.java`
- [x] T006 [P] 创建 DataRetentionExecution 实体类 `backend/src/main/java/com/crm/model/entity/DataRetentionExecution.java`
- [x] T007 [P] 创建 DataArchiveLog 实体类 `backend/src/main/java/com/crm/model/entity/DataArchiveLog.java`
- [x] T008 创建 DataRetentionPolicyRepository 接口 `backend/src/main/java/com/crm/repository/DataRetentionPolicyRepository.java`
- [x] T009 创建 DataRetentionExecutionRepository 接口 `backend/src/main/java/com/crm/repository/DataRetentionExecutionRepository.java`
- [x] T010 创建 DataArchiveLogRepository 接口 `backend/src/main/java/com/crm/repository/DataArchiveLogRepository.java`
- [x] T011 创建 DTO 类（DataRetentionPolicyRequest、DataRetentionPolicyResponse、DataRetentionExecutionResponse）
- [x] T012 创建 DataRetentionPolicyService 接口及基础方法签名
- [x] T013 创建 DataRetentionPolicyController 基础路由（暂不实现）
- [x] T014 创建前端 API 客户端 `frontend/src/services/api/dataRetentionApi.ts`（基础函数签名）
- [x] T015 验证 Flyway 迁移执行成功（`mvn flyway:migrate`）

**Checkpoint**: 基础架构就绪 - 用户故事实现现在可以开始

## Phase 3: User Story 1 - 配置数据保留策略（优先级：P1）🎯 MVP

**Goal**: 管理员为不同数据实体设置保留期限，系统支持按实体类型配置。

**Independent Test**: 创建数据保留策略 → 策略保存成功 → 策略生效。

### Implementation for User Story 1

- [x] T016 [US1] 实现 DataRetentionPolicyService.createPolicy() - 创建数据保留策略
- [x] T017 [US1] 实现 DataRetentionPolicyController.createPolicy() - POST /api/v1/data-retention/policies
- [x] T018 [US1] 实现 DataRetentionPolicyController.getPolicies() - GET /api/v1/data-retention/policies
- [x] T019 [US1] 添加 DTO 校验注解（@NotNull、@Min for 保留期限）
- [x] T020 [US1] 添加业务校验（同一实体类型唯一策略、保留期限 > 0）
- [x] T021 [US1] 创建前端策略列表页面 `frontend/src/pages/settings/DataRetentionPolicyListPage.tsx`（ProTable + 统计卡片）
- [x] T022 [US1] 创建前端策略创建页面 `frontend/src/pages/settings/DataRetentionPolicyCreatePage.tsx`（表单）
- [x] T023 [US1] 实现前端实体选择器（客户/商机/合同/订单/发票/跟进记录/审计日志）
- [x] T024 [US1] 实现前端保留期限输入（年/月/日）
- [x] T025 [US1] 实现前端归档方式选择器（归档到归档表/直接删除）
- [x] T026 [US1] 添加路由配置 `/settings/data-retention`、`/settings/data-retention/create`
- [x] T027 [US1] 验证 API 端点（创建策略 → 列表查询）
- [x] T028 [US1] 验证前端页面（创建策略 → 列表显示）

**Checkpoint**: User Story 1 应完全可用且可独立测试

## Phase 4: User Story 2 - 自动归档与删除（优先级：P1）

**Goal**: 系统在设定时间自动扫描到期数据，将到期数据归档到归档表，超期数据自动删除。

**Independent Test**: 到期数据到达保留期限 → 自动归档 → 归档记录可追溯。

### Implementation for User Story 2

- [x] T029 [US2] 实现 DataRetentionScheduler - Spring Schedule 定时调度（@Scheduled）
- [x] T030 [US2] 实现 DataRetentionPolicyService.executeArchive() - 执行归档（分批 1000 条）
- [x] T031 [US2] 实现归档表动态创建（实体 + _archive 后缀，结构与主表一致）
- [x] T032 [US2] 实现归档数据移动（主表 → 归档表，添加 archived_at 字段）
- [x] T033 [US2] 实现归档日志创建（DataArchiveLog，记录源表、源记录 ID、归档时间）
- [x] T034 [US2] 实现超期数据删除（归档表数据删除）
- [x] T035 [US2] 实现执行记录创建（DataRetentionExecution，状态 = SUCCESS/FAILED）
- [x] T036 [US2] 实现归档锁机制（避免并发访问冲突）
- [x] T037 [US2] 验证归档执行（修改策略保留期限为 0，手动触发归档）
- [x] T038 [US2] 验证归档日志（DataArchiveLog 记录）
- [x] T039 [US2] 验证归档表结构（customer_archive 表存在）

**Checkpoint**: User Stories 1 AND 2 都应独立工作

## Phase 5: User Story 3 - 合规导出（优先级：P2）

**Goal**: 用户可导出指定时间段内的数据，支持 CSV/Excel 格式，满足数据主体访问请求。

**Independent Test**: 创建合规导出任务 → 导出指定时间段数据 → 文件下载。

### Implementation for User Story 3

- [x] T040 [US3] 实现 DataRetentionPolicyService.exportData() - 合规导出（复用 016 导出逻辑）
- [x] T041 [US3] 实现 DataRetentionPolicyController.exportData() - POST /api/v1/data-retention/export
- [x] T042 [US3] 实现 DataRetentionPolicyController.downloadExport() - GET /api/v1/data-retention/export/{id}/download
- [x] T043 [US3] 实现导出文件生成（CSV/Excel 格式）
- [x] T044 [US3] 实现导出文件存储（本地文件系统，7 天后过期）
- [x] T045 [US3] 实现导出进度跟踪（大数据量异步导出）
- [x] T046 [US3] 创建前端合规导出页面 `frontend/src/pages/settings/DataRetentionExportPage.tsx`
- [x] T047 [US3] 实现前端实体选择器 + 期间选择器
- [x] T048 [US3] 实现前端导出格式选择器（CSV/Excel）
- [x] T049 [US3] 实现前端导出按钮（调用导出 API）
- [x] T050 [US3] 验证导出功能（导出 2024 年客户数据 → 文件下载）

**Checkpoint**: User Stories 1, 2, AND 3 都应独立工作

## Phase 6: User Story 4 - 策略管理与审计（优先级：P2）

**Goal**: 管理员可查看/修改/删除数据保留策略，系统记录所有策略变更的审计日志。

**Independent Test**: 修改策略 → 审计日志记录变更 → 新策略生效。

### Implementation for User Story 4

- [x] T051 [US4] 实现 DataRetentionPolicyService.updatePolicy() - 更新策略
- [x] T052 [US4] 实现 DataRetentionPolicyService.updateStatus() - 暂停/恢复策略
- [x] T053 [US4] 实现 DataRetentionPolicyService.deletePolicy() - 删除策略
- [x] T054 [US4] 实现 DataRetentionPolicyController.updatePolicy() - PUT /api/v1/data-retention/policies/{id}
- [x] T055 [US4] 实现 DataRetentionPolicyController.updateStatus() - PUT /api/v1/data-retention/policies/{id}/status
- [x] T056 [US4] 实现 DataRetentionPolicyController.deletePolicy() - DELETE /api/v1/data-retention/policies/{id}
- [x] T057 [US4] 实现策略变更审计日志（记录变更人、变更前后值、变更时间）
- [x] T058 [US4] 实现暂停逻辑（跳过下次执行，状态 = SUSPENDED）
- [x] T059 [US4] 实现恢复逻辑（重新计算下次执行时间，状态 = ACTIVE）
- [x] T060 [US4] 实现前端策略管理功能（暂停/恢复/删除按钮）
- [x] T061 [US4] 实现前端状态切换（暂停 ↔ 恢复）
- [x] T062 [US4] 实现前端删除确认对话框
- [x] T063 [US4] 验证暂停功能（暂停 → 跳过执行 → 状态 = SUSPENDED）
- [x] T064 [US4] 验证恢复功能（恢复 → 重新计算时间 → 状态 = ACTIVE）
- [x] T065 [US4] 验证删除功能（删除 → 状态 = DELETED → 执行历史保留）

**Checkpoint**: All user stories should now be independently functional

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: 影响多个用户故事的改进

- [x] T066 [P] 添加后端单元测试 `backend/src/test/java/com/crm/service/DataRetentionPolicyServiceTest.java`
- [x] T067 [P] 添加后端控制器测试 `backend/src/test/java/com/crm/controller/DataRetentionPolicyControllerTest.java`
- [x] T068 [P] 添加前端页面测试 `frontend/tests/settings/DataRetentionPolicyListPage.test.tsx`
- [x] T069 添加审计日志集成（策略变更/归档/删除记录到 AuditLog）
- [x] T070 添加数据权限校验（只有管理员可管理策略）
- [x] T071 [P] 添加 i18n 翻译键（数据保留策略相关文案）
- [x] T072 运行 quickstart.md 验证场景
- [x] T073 前端 typecheck/lint/build 验证
- [x] T074 后端 verify 验证（`mvn verify`）
- [x] T075 更新 roadmap.md 记录 080 模块交付

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
