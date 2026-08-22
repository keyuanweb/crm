# Tasks: 任务与提醒模块

**Input**: Design documents from `/specs/010-task-reminder/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2/US3/US4

---

## Phase 1: 数据模型与基础

- [x] T001 [P] 创建 Flyway 迁移 V22 in `backend/src/main/resources/db/migration/V22__task_item.sql`（task_item 表 + owner/status/due 索引）
- [x] T002 [P] 创建 TaskItem 实体/Mapper in `backend/src/main/java/com/crm/entity/TaskItem.java` + `backend/src/main/java/com/crm/repository/TaskItemMapper.java`
- [x] T003 [P] H2 测试 schema 同步 task_item 表 in `backend/src/test/resources/schema-h2.sql`
- [x] T004 [P] ErrorCode 新增 TASK_NOT_FOUND in `backend/src/main/java/com/crm/common/ErrorCode.java`

**Checkpoint**: 数据模型就绪

---

## Phase 2: 用户故事 1 - 待办任务管理 (P0) 🎯 MVP

**Goal**: 任务 CRUD + 完成/重开 + 本人数据隔离。

**Independent Test**: 创建任务→列表可见→完成→重开。

### 实现

- [x] T005 [P] [US1] 创建 TaskRequest/TaskResponse DTO in `backend/src/main/java/com/crm/dto/task/`
- [x] T006 [US1] 创建 TaskService in `backend/src/main/java/com/crm/service/TaskService.java`（CRUD/完成重开/owner 隔离/归属校验）
- [x] T007 [US1] 创建 TaskController in `backend/src/main/java/com/crm/controller/TaskController.java`（GET/POST/PUT/toggle/DELETE，类级全员）
- [x] T008 [US1] 创建 TaskServiceTest 单元测试 in `backend/src/test/java/com/crm/service/TaskServiceTest.java`（CRUD/非本人 403/完成重开）
- [x] T009 [US1] 创建 TaskIT 集成测试（任务部分）in `backend/src/test/java/com/crm/integration/TaskIT.java`
- [x] T010 [US1] 前端类型与服务 in `frontend/src/types/task.ts` + `frontend/src/services/taskService.ts`
- [x] T011 [US1] 任务列表页 in `frontend/src/pages/tasks/TaskListPage.tsx`（ProTable + 新增/编辑/完成/重开/删除弹窗）

**Checkpoint**: US1 可用——任务管理完整

---

## Phase 3: 用户故事 2 - 截止提醒 (P0)

**Goal**: 逾期/今日到期标识 + 提醒汇总。

**Independent Test**: 构造逾期/今日任务→标识正确→汇总正确。

### 实现

- [x] T012 [US2] TaskService 增加提醒标识计算（OVERDUE 含天数/TODAY/NORMAL/DONE）与 reminder-summary in `backend/src/main/java/com/crm/service/TaskService.java`
- [x] T013 [US2] TaskController 增加 GET /tasks/reminder-summary in `backend/src/main/java/com/crm/controller/TaskController.java`
- [x] T014 [US2] TaskServiceTest 增加逾期/今日边界用例 in `backend/src/test/java/com/crm/service/TaskServiceTest.java`
- [x] T015 [US2] TaskIT 增加提醒标识与汇总用例 in `backend/src/test/java/com/crm/integration/TaskIT.java`
- [x] T016 [US2] 前端列表页增加提醒汇总卡片与逾期/今日 Tag in `frontend/src/pages/tasks/TaskListPage.tsx`

**Checkpoint**: US2 可用——截止提醒

---

## Phase 4: 用户故事 3 - 日历视图 (P1)

**Goal**: 按月日历数据 + 前端 Calendar 渲染。

**Independent Test**: 有任务日期标记→切换月份→正确。

### 实现

- [x] T017 [US3] TaskService 增加 calendar 按月查询（date → tasks，含逾期标记）in `backend/src/main/java/com/crm/service/TaskService.java`
- [x] T018 [US3] TaskController 增加 GET /tasks/calendar in `backend/src/main/java/com/crm/controller/TaskController.java`
- [x] T019 [US3] TaskIT 增加日历用例 in `backend/src/test/java/com/crm/integration/TaskIT.java`
- [x] T020 [US3] 前端日历视图页 in `frontend/src/pages/tasks/TaskCalendarPage.tsx`（antd Calendar + 逾期红色 + 点击日期弹层）

**Checkpoint**: US3 可用——日历视图

---

## Phase 5: 用户故事 4 - 跟进计划 (P2)

**Goal**: 添加跟随时可选自动创建任务。

**Independent Test**: 勾选创建任务→任务列表出现关联任务。

### 实现

- [x] T021 [US4] FollowUpRequest 增加 createTask 字段 in `backend/src/main/java/com/crm/dto/followup/FollowUpRequest.java`
- [x] T022 [US4] FollowUpService 注入 TaskService，create 时勾选则自动创建跟进任务（title=跟进：客户/线索名，due=nextFollowUpAt）in `backend/src/main/java/com/crm/service/FollowUpService.java`
- [x] T023 [US4] FollowUpServiceTest/现有测试适配新构造参数 in `backend/src/test/java/com/crm/service/`（如有）
- [x] T024 [US4] TaskIT 增加跟进自动建任务用例 in `backend/src/test/java/com/crm/integration/TaskIT.java`
- [x] T025 [US4] 前端跟进弹窗增加"创建跟进任务"勾选 in `frontend/src/components/FollowUpTimeline.tsx`

**Checkpoint**: US4 可用——跟进计划

---

## Phase 6: 收尾与验证

- [x] T026 前端路由与菜单 in `frontend/src/App.tsx`（任务菜单 + 列表/日历路由）
- [x] T027 Backend `mvn verify` 通过（含新增测试、spotless、JaCoCo）
- [x] T028 单独运行 `mvn test "-Dtest=TaskIT"` 通过
- [x] T029 Frontend typecheck / lint / test / build 通过
- [x] T030 线上端点验证（任务 CRUD→逾期/今日标识→汇总→日历→toggle→跟进自动建任务→数据隔离）
- [x] T031 更新契约文档（按实现校正）与 roadmap 010 标记 `[x]`

**Checkpoint**: 模块完整可用
