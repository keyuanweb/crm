# Tasks: 客户标签与细分

**Input**: Design documents from `/specs/031-customer-tags/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/customer-tags.md

**Tests**: 章程原则四要求测试先于实现（红→绿），本功能含后端单元/集成 + 前端测试。

## Phase 1: 基础设施

- [x] T001 [P] [US1] 后端：`db/migration/V47__tags_segments.sql`——tag/customer_tag/segment 三表。
- [x] T002 [P] [US1] 后端：实体 Tag/CustomerTag/Segment + 3 Mapper。
- [x] T003 [P] [US1] 后端：RoleConstants 权限字典加 `tag:manage`。

## Phase 2: 后端测试先行（TDD 红）

- [x] T004 [P] [US1] 后端：`TagServiceTest`——标签 CRUD、打标（覆盖式）、客户标签查询、删除级联。红阶段。
- [x] T005 [P] [US2] 后端：`SegmentServiceTest`——细分 CRUD、条件解析（tag/amount/lastFollowUpDays + AND/OR）、成员计算。红阶段。
- [x] T006 [P] [US1] 后端：`integration/TagIT.java`——建标签→打标→客户标签→细分成员。红阶段。

## Phase 3: 后端实现

- [x] T007 [US1] 后端：DTO（TagRequest/TagResponse/SegmentRequest/SegmentResponse）。
- [x] T008 [US1] 后端：`TagService`——标签 CRUD + 打标（覆盖式 + 数据权限）+ 客户标签查询 + 删除级联。（依赖 T002）
- [x] T009 [US2] 后端：`SegmentService`——细分 CRUD + 条件 JSON 解析校验 + 成员计算（可见客户集 → tag join/amount 聚合/lastFollowUpDays 过滤 → AND/OR 组合）。（依赖 T002/T008）
- [x] T010 [US1] 后端：`TagController`（/api/v1/tags + /customers/{id}/tags）+ `SegmentController`（/api/v1/segments + members/count）。（依赖 T008/T009）

## Phase 4: 前端

- [x] T011 [P] [US1] 前端：`types/tag.ts` + `tagService.ts` + `segmentService.ts`。
- [x] T012 [US1] 前端：`TagListPage`（标签管理页，系统管理分组）+ `SegmentListPage`（细分管理页，条件编辑器 + 成员数预览）。（依赖 T011）
- [x] T013 [US1] 前端：CustomerListPage 加标签列 + 标签筛选；App.tsx 注册路由。（依赖 T012）

## Phase 5: 验证与收尾

- [x] T014 后端：`mvn test` 全量通过；H2 schema 同步新表。
- [x] T015 前端：`pnpm run typecheck` + `lint` + `test` 全量通过。
- [x] T016 [P] 手动冒烟：建标签→打标→筛选→建细分→成员计算→客户列表标签列。

## Dependencies & Execution Order

- T001/T002/T003 可并行。
- T004/T005/T006 可并行，均红阶段；依赖 T001-T003。
- T007 依赖 T002；T008 依赖 T007；T009 依赖 T008；T010 依赖 T008/T009。
- T011 无依赖；T012 依赖 T011；T013 依赖 T012。
- Phase 5 完成后收尾。

## Notes

- 细分条件 JSON：logic(AND/OR) + filters(field: tag/amount/lastFollowUpDays, op: IN/GT/GTE/LT/LTE)。
- 打标/成员按 012 数据权限（ADMIN 全量/SALES 本人）。
- 删除标签级联清关联；细分条件白名单校验。
