# Tasks: 外勤拜访管理

**Input**: Design documents from `/specs/035-field-visit/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/field-visit.md

**Tests**: 章程原则四要求测试先于实现（红→绿），本功能含后端单元/集成 + 前端测试。

## Phase 1: 基础设施

- [x] T001 [P] [US1] 后端：`db/migration/V50__field_visit.sql`——field_visit 表。
- [x] T002 [P] [US1] 后端：实体 FieldVisit + Mapper。
- [x] T003 [P] [US1] 后端：RoleConstants 权限字典加 `visit:manage`。

## Phase 2: 后端测试先行（TDD 红）

- [x] T004 [P] [US1] 后端：`FieldVisitServiceTest`——计划 CRUD、签到（坐标/时间记录 + 防重复拒绝 + 小结转跟进）、取消、统计。红阶段。
- [x] T005 [P] [US1] 后端：`integration/FieldVisitIT.java`——建计划→签到→跟进记录出现→统计。红阶段。

## Phase 3: 后端实现

- [x] T006 [US1] 后端：DTO（VisitRequest/VisitResponse/CheckInRequest/VisitStatsResponse）。
- [x] T007 [US1] 后端：`FieldVisitService`——计划 CRUD（PLANNED 可编辑/取消）+ 签到（乐观防重复 + 坐标/时间/小结）+ 小结转 follow_up（type=VISIT）+ 统计（按销售/月份完成率）。（依赖 T002/T006 + FollowUpMapper）
- [x] T008 [US1] 后端：`FieldVisitController`——/api/v1/field-visits（CRUD/cancel/check-in/stats）。（依赖 T007）

## Phase 4: 前端

- [x] T009 [P] [US1] 前端：`types/visit.ts` + `visitService.ts`。
- [x] T010 [US1] 前端：`VisitListPage`（拜访列表 + 统计卡 + 新建/签到/取消 + 详情签到弹窗（Geolocation 定位 + 小结））。（依赖 T009）
- [x] T011 [US1] 前端：App.tsx 路由注册（销售分组）。（依赖 T010）

## Phase 5: 验证与收尾

- [x] T012 后端：`mvn test` 全量通过；H2 schema 同步新表。
- [x] T013 前端：`pnpm run typecheck` + `lint` + `test` 全量通过。
- [x] T014 [P] 手动冒烟：建拜访计划→签到（定位）→小结→客户跟进时间线拜访记录→统计。

## Dependencies & Execution Order

- T001/T002/T003 可并行。
- T004/T005 可并行，均红阶段；依赖 T001/T002。
- T006 依赖 T002；T007 依赖 T006；T008 依赖 T007。
- T009 无依赖；T010 依赖 T009；T011 依赖 T010。
- Phase 5 完成后收尾。

## Notes

- 签到防重复：仅 PLANNED 可签到（乐观 UPDATE WHERE status=PLANNED）。
- 小结自动写 follow_up（type=VISIT）→ 客户时间线。
- 统计：按销售/月份计划数/完成数/完成率。
- 权限 visit:manage 入 028 字典。
