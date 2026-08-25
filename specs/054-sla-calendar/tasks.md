# Tasks: SLA 工作时间与节假日日历模块

**Input**: Design documents from `/specs/054-sla-calendar/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2

---

## Phase 1: 数据模型与基础

- [ ] T001 [P] 创建 Flyway 迁移 V61 in `backend/src/main/resources/db/migration/V61__sla_calendar_config.sql`
- [ ] T002 [P] H2 测试 schema 同步 in `backend/src/test/resources/schema-h2.sql`
- [ ] T003 [P] 创建 SlaCalendarConfig 实体 + Mapper in `backend/src/main/java/com/crm/entity/` + `backend/src/main/java/com/crm/repository/`
- [ ] T004 [P] ErrorCode 新增 SLA_CALENDAR_* 错误码 in `backend/src/main/java/com/crm/common/ErrorCode.java`
- [ ] T005 [P] 创建 SlaCalendarConfigRequest/Response DTO in `backend/src/main/java/com/crm/dto/sla/`

**Checkpoint**: 数据模型就绪

---

## Phase 2: 用户故事 1/2 - 配置与计算 (P0)

**Goal**: 工作时间/节假日配置 + SLA 窗口计算。

**Independent Test**: 配置→跨周末/跨假日工单 SLA 正确。

### 实现

- [ ] T006 [P] 创建 SlaCalendarService（配置 CRUD + advanceWorkingTime 纯函数）in `backend/src/main/java/com/crm/service/SlaCalendarService.java`
- [ ] T007 创建 SlaCalendarController（/sla-calendar，仅 ADMIN）in `backend/src/main/java/com/crm/controller/SlaCalendarController.java`
- [ ] T008 TicketService.applySla 改用日历计算（无配置回退）in `backend/src/main/java/com/crm/service/TicketService.java`
- [ ] T009 创建 SlaCalendarServiceTest 单元测试（跨周末/跨夜/节假日/回退）in `backend/src/test/java/com/crm/service/SlaCalendarServiceTest.java`
- [ ] T010 创建 SlaCalendarIT 集成测试 in `backend/src/test/java/com/crm/integration/SlaCalendarIT.java`
- [ ] T011 前端类型/服务 in `frontend/src/types/slaCalendar.ts` + `frontend/src/services/slaCalendarService.ts`
- [ ] T012 配置页 in `frontend/src/pages/sla/SlaCalendarPage.tsx`（工作时间/工作周/节假日/启用）

**Checkpoint**: US1/2 可用——日历配置与计算

---

## Phase 3: 收尾与验证

- [ ] T013 App.tsx 点亮"SLA日历"占位项为路由 in `frontend/src/App.tsx`（/sla-calendar）
- [ ] T014 Backend `mvn verify` 通过（含新增测试、spotless、JaCoCo）
- [ ] T015 单独运行 `mvn test "-Dtest=SlaCalendarIT"` 通过
- [ ] T016 Frontend typecheck / lint / test / build 通过
- [ ] T017 线上端点验证（配置→跨周末工单 SLA→无配置回退）
- [ ] T018 更新契约文档（按实现校正）与 roadmap 054 标记 `[x]`

**Checkpoint**: 模块完整可用

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1**: 无依赖
- **Phase 2**: 依赖 Phase 1；T008 需在 T006 后
- **Phase 3**: 依赖全部完成

## Notes

- advanceWorkingTime 为纯函数（可独立单测）
- 无配置回退 24h（兼容既有测试）
- 提交规范：Conventional Commits
