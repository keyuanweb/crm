# Tasks: 定时导出订阅（Scheduled Export Subscription）

**Input**: Design documents from `/specs/079-scheduled-export/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/scheduled-export-api.md

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 项目初始化和基础结构

- [x] T001 创建后端包结构 `backend/src/main/java/com/crm/model/entity/`、`repository/`、`service/`、`controller/`
- [x] T002 创建前端包结构 `frontend/src/pages/exports/`、`frontend/src/services/api/`
- [x] T003 [P] 确认 Spring Schedule 框架可用（Spring Boot 内置）

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 核心基础设施 - 必须在任何用户故事之前完成

**⚠️ CRITICAL**: 无用户故事工作可在此阶段完成前开始

- [x] T004 创建 Flyway 迁移脚本 `V73__scheduled_export.sql`（2 张表 + 索引 + 约束）
- [x] T005 [P] 创建 ScheduledExport 实体类 `backend/src/main/java/com/crm/model/entity/ScheduledExport.java`
- [x] T006 [P] 创建 ScheduledExportExecution 实体类 `backend/src/main/java/com/crm/model/entity/ScheduledExportExecution.java`
- [x] T007 创建 ScheduledExportRepository 接口 `backend/src/main/java/com/crm/repository/ScheduledExportRepository.java`
- [x] T008 创建 ScheduledExportExecutionRepository 接口 `backend/src/main/java/com/crm/repository/ScheduledExportExecutionRepository.java`
- [x] T009 创建 DTO 类（ScheduledExportRequest、ScheduledExportResponse、ScheduledExportExecutionResponse）
- [x] T010 创建 ScheduledExportService 接口及基础方法签名
- [x] T011 创建 ScheduledExportController 基础路由（暂不实现）
- [x] T012 创建前端 API 客户端 `frontend/src/services/api/scheduledExportApi.ts`（基础函数签名）
- [x] T013 验证 Flyway 迁移执行成功（`mvn flyway:migrate`）

**Checkpoint**: 基础架构就绪 - 用户故事实现现在可以开始

## Phase 3: User Story 1 - 创建定时导出任务（优先级：P1）🎯 MVP

**Goal**: 用户选择导出实体、筛选条件、导出格式，设置执行周期，系统自动创建定时任务。

**Independent Test**: 创建定时导出任务 → 任务保存成功 → 下次执行时间正确计算。

### Implementation for User Story 1

- [x] T014 [US1] 实现 ScheduledExportService.createScheduledExport() - 创建定时导出任务
- [x] T015 [US1] 实现 ScheduledExportService.calculateNextExecutionTime() - 计算下次执行时间（Cron 表达式解析）
- [x] T016 [US1] 实现 ScheduledExportController.createScheduledExport() - POST /api/v1/scheduled-exports
- [x] T017 [US1] 实现 ScheduledExportController.getScheduledExports() - GET /api/v1/scheduled-exports
- [x] T018 [US1] 添加 DTO 校验注解（@NotNull、@Pattern for Cron 表达式）
- [x] T019 [US1] 添加业务校验（Cron 表达式合法性、筛选条件字段存在性、单用户任务数限制 ≤10）
- [x] T020 [US1] 创建前端定时导出列表页面 `frontend/src/pages/exports/ScheduledExportListPage.tsx`（ProTable + 统计卡片）
- [x] T021 [US1] 创建前端定时导出创建页面 `frontend/src/pages/exports/ScheduledExportCreatePage.tsx`（表单 + Cron 预览）
- [x] T022 [US1] 实现前端实体选择器（客户/商机/合同/订单/发票）
- [x] T023 [US1] 实现前端筛选条件表单（根据实体动态生成）
- [x] T024 [US1] 实现前端执行周期选择器（每日/每周/每月，指定时间）
- [x] T025 [US1] 实现前端 Cron 表达式预览（用户选择周期后显示对应 Cron）
- [x] T026 [US1] 添加路由配置 `/exports/scheduled`、`/exports/scheduled/create`
- [x] T027 [US1] 验证 API 端点（创建任务 → 列表查询）
- [x] T028 [US1] 验证前端页面（创建任务 → 列表显示）

**Checkpoint**: User Story 1 应完全可用且可独立测试

## Phase 4: User Story 2 - 定时执行与邮件推送（优先级：P1）

**Goal**: 系统在设定时间自动执行导出任务，生成文件后通过邮件发送给任务创建者。

**Independent Test**: 定时任务到达执行时间 → 自动执行导出 → 邮件发送成功。

### Implementation for User Story 2

- [x] T029 [US2] 实现 ScheduledExportScheduler - Spring Schedule 定时调度（@Scheduled）
- [x] T030 [US2] 实现 ScheduledExportService.executeScheduledExport() - 执行定时导出（复用 016 导出逻辑）
- [x] T031 [US2] 实现 ScheduledExportService.sendExportEmail() - 邮件发送（复用 030 邮件发送能力）
- [x] T032 [US2] 实现执行记录创建（ScheduledExportExecution，状态 = SUCCESS/FAILED）
- [x] T033 [US2] 实现邮件发送失败重试逻辑（最多 3 次，间隔 5 分钟）
- [x] T034 [US2] 实现导出文件存储（本地文件系统 `/tmp/exports/`）
- [x] T035 [US2] 实现导出文件清理（邮件发送后删除，过期文件 >7 天定时清理）
- [x] T036 [US2] 实现数据量限制校验（最多 10 万行，超出则拒绝）
- [x] T037 [US2] 验证定时执行（修改 Cron 为当前时间 +1 分钟，等待执行）
- [x] T038 [US2] 验证邮件发送（检查收件人邮箱）
- [x] T039 [US2] 验证执行记录（状态 = EMAIL_SENT）

**Checkpoint**: User Stories 1 AND 2 都应独立工作

## Phase 5: User Story 3 - 执行历史与手动触发（优先级：P2）

**Goal**: 用户可查看定时导出任务的执行历史，支持手动立即执行。

**Independent Test**: 查看执行历史 → 显示所有执行记录；手动触发 → 立即执行并发送邮件。

### Implementation for User Story 3

- [x] T040 [US3] 实现 ScheduledExportService.getExecutions() - 获取执行历史
- [x] T041 [US3] 实现 ScheduledExportService.executeNow() - 手动立即执行
- [x] T042 [US3] 实现 ScheduledExportController.getExecutions() - GET /api/v1/scheduled-exports/{id}/executions
- [x] T043 [US3] 实现 ScheduledExportController.executeNow() - POST /api/v1/scheduled-exports/{id}/execute-now
- [x] T044 [US3] 实现执行历史分页查询
- [x] T045 [US3] 实现执行状态展示（SUCCESS/FAILED/EMAIL_SENT/EMAIL_FAILED）
- [x] T046 [US3] 创建前端执行历史页面 `frontend/src/pages/exports/ScheduledExportExecutionHistoryPage.tsx`
- [x] T047 [US3] 实现前端执行历史表格（时间、状态、文件大小、邮件状态）
- [x] T048 [US3] 实现前端"立即执行"按钮（调用 executeNow API）
- [x] T049 [US3] 验证执行历史查询（多页数据）
- [x] T050 [US3] 验证手动执行（立即执行 → 邮件发送 → 执行历史更新）

**Checkpoint**: User Stories 1, 2, AND 3 都应独立工作

## Phase 6: User Story 4 - 任务管理与暂停/恢复（优先级：P2）

**Goal**: 用户可暂停/恢复/删除定时导出任务，管理员可查看所有定时任务。

**Independent Test**: 暂停任务 → 下次执行被跳过；恢复任务 → 下次执行恢复正常。

### Implementation for User Story 4

- [x] T051 [US4] 实现 ScheduledExportService.updateStatus() - 更新任务状态（暂停/恢复/删除）
- [x] T052 [US4] 实现 ScheduledExportController.updateStatus() - PUT /api/v1/scheduled-exports/{id}/status
- [x] T053 [US4] 实现 ScheduledExportController.deleteScheduledExport() - DELETE /api/v1/scheduled-exports/{id}
- [x] T054 [US4] 实现暂停逻辑（跳过下次执行，状态 = SUSPENDED）
- [x] T055 [US4] 实现恢复逻辑（重新计算下次执行时间，状态 = ACTIVE）
- [x] T056 [US4] 实现删除逻辑（状态 = DELETED，保留执行历史）
- [x] T057 [US4] 创建前端任务管理功能（暂停/恢复/删除按钮）
- [x] T058 [US4] 实现前端状态切换（暂停 ↔ 恢复）
- [x] T059 [US4] 实现前端删除确认对话框
- [x] T060 [US4] 验证暂停功能（暂停 → 跳过执行 → 状态 = SUSPENDED）
- [x] T061 [US4] 验证恢复功能（恢复 → 重新计算时间 → 状态 = ACTIVE）
- [x] T062 [US4] 验证删除功能（删除 → 状态 = DELETED → 执行历史保留）

**Checkpoint**: All user stories should now be independently functional

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: 影响多个用户故事的改进

- [x] T063 [P] 添加后端单元测试 `backend/src/test/java/com/crm/service/ScheduledExportServiceTest.java`
- [x] T064 [P] 添加后端控制器测试 `backend/src/test/java/com/crm/controller/ScheduledExportControllerTest.java`
- [x] T065 [P] 添加前端页面测试 `frontend/tests/exports/ScheduledExportListPage.test.tsx`
- [x] T066 添加审计日志集成（任务创建/暂停/恢复/删除记录到 AuditLog）
- [x] T067 添加数据权限校验（用户只能看自己的定时任务）
- [x] T068 [P] 添加 i18n 翻译键（定时导出相关文案）
- [x] T069 运行 quickstart.md 验证场景
- [x] T070 前端 typecheck/lint/build 验证
- [x] T071 后端 verify 验证（`mvn verify`）
- [x] T072 更新 roadmap.md 记录 079 模块交付

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
