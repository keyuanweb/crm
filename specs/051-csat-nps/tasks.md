# Tasks: 工单满意度调查（CSAT/NPS）模块

**Input**: Design documents from `/specs/051-csat-nps/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2

---

## Phase 1: 数据模型与基础

- [ ] T001 [P] 创建 Flyway 迁移 V59 in `backend/src/main/resources/db/migration/V59__ticket_survey.sql`（ticket_survey 表 + uk_survey_ticket 唯一）
- [ ] T002 [P] H2 测试 schema 同步 in `backend/src/test/resources/schema-h2.sql`
- [ ] T003 [P] 创建 TicketSurvey 实体 + Mapper in `backend/src/main/java/com/crm/entity/` + `backend/src/main/java/com/crm/repository/`
- [ ] T004 [P] ErrorCode 新增 SURVEY_* 错误码 in `backend/src/main/java/com/crm/common/ErrorCode.java`
- [ ] T005 [P] 创建 SurveyRequest/SurveyResponse/SurveyStatsResponse DTO in `backend/src/main/java/com/crm/dto/survey/`

**Checkpoint**: 数据模型就绪

---

## Phase 2: 用户故事 1 - 工单满意度评分 (P0)

**Goal**: CLOSED 工单评分（唯一/状态校验）。

**Independent Test**: 关闭→评分→记录→重复 409。

### 实现

- [ ] T006 [US1] 创建 TicketSurveyService（评分提交/查询）in `backend/src/main/java/com/crm/service/TicketSurveyService.java`
- [ ] T007 [US1] 创建 TicketSurveyController（/tickets/{id}/survey，ADMIN+SERVICE）in `backend/src/main/java/com/crm/controller/TicketSurveyController.java`
- [ ] T008 [US1] 创建 TicketSurveyServiceTest 单元测试 in `backend/src/test/java/com/crm/service/TicketSurveyServiceTest.java`

**Checkpoint**: US1 可用——工单评分

---

## Phase 3: 用户故事 2 - 满意度统计（CSAT/NPS） (P0)

**Goal**: CSAT 均值 + NPS 分布统计。

**Independent Test**: 多评分 → 统计正确。

### 实现

- [ ] T009 [P] [US2] TicketSurveyService 增加 stats 聚合（均值/NPS/三档）in `backend/src/main/java/com/crm/service/TicketSurveyService.java`
- [ ] T010 [US2] TicketSurveyController 增加 /surveys/stats in `backend/src/main/java/com/crm/controller/TicketSurveyController.java`
- [ ] T011 [US2] 创建 TicketSurveyIT 集成测试 in `backend/src/test/java/com/crm/integration/TicketSurveyIT.java`
- [ ] T012 [US2] 前端类型/服务 in `frontend/src/types/survey.ts` + `frontend/src/services/ticketSurveyService.ts`
- [ ] T013 [US2] 工单详情评分区块（CLOSED 可评/已评分展示）in `frontend/src/pages/tickets/`
- [ ] T014 [US2] 满意度统计页 in `frontend/src/pages/surveys/SatisfactionStatsPage.tsx`（CSAT/NPS/分布/时间筛选）

**Checkpoint**: US2 可用——满意度统计

---

## Phase 4: 收尾与验证

- [ ] T015 App.tsx 点亮"满意度调查"占位项为路由 in `frontend/src/App.tsx`（/satisfaction）
- [ ] T016 Backend `mvn verify` 通过（含新增测试、spotless、JaCoCo）
- [ ] T017 单独运行 `mvn test "-Dtest=TicketSurveyIT"` 通过
- [ ] T018 Frontend typecheck / lint / test / build 通过
- [ ] T019 线上端点验证（关闭→评分→重复 409→统计）
- [ ] T020 更新契约文档（按实现校正）与 roadmap 051 标记 `[x]`

**Checkpoint**: 模块完整可用

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1**: 无依赖
- **Phase 2 (US1)**: 依赖 Phase 1
- **Phase 3 (US2)**: 依赖 Phase 1；stats 复用评分数据
- **Phase 4**: 依赖全部完成

### Parallel Opportunities

- Phase 1 的 T001~T005 全部 [P]
- US2 统计服务与前端可并行

## Notes

- 评分 1-5；NPS 分档 1-3 贬损/4 中立/5 推荐
- v1 内部代录（客户门户 050 后续对接自助评分）
- 提交规范：Conventional Commits
