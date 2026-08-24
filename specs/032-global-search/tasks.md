# Tasks: 全局搜索

**Input**: Design documents from `/specs/032-global-search/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/global-search.md

**Tests**: 章程原则四要求测试先于实现（红→绿），本功能含后端单元/集成 + 前端测试。

## Phase 1: 后端测试先行（TDD 红）

- [x] T001 [P] [US1] 后端：`SearchServiceTest`——客户/线索/联系人/商机/工单/产品 LIKE 匹配、多关键字 AND、数据权限过滤。红阶段。
- [x] T002 [P] [US1] 后端：`integration/SearchIT.java`——搜"客户"关键字返回分组结果、点击 path 正确。红阶段。

## Phase 2: 后端实现

- [x] T003 [US1] 后端：`dto/search/SearchResponse.java`（keyword + groups）+ SearchGroup/SearchItem。
- [x] T004 [US1] 后端：`SearchService`——6 实体 LIKE 聚合（字段见 contracts）+ 多关键字 AND + 数据权限（ADMIN 全量/其他按 created_by/owner）+ 下拉 Top 5 + 结果页分页。
- [x] T005 [US1] 后端：`SearchController`——GET /api/v1/search（下拉）+ GET /api/v1/search/full（结果页 type 过滤）。（依赖 T004）

## Phase 3: 前端

- [x] T006 [P] [US1] 前端：`services/searchService.ts`（searchAll/searchFull）。
- [x] T007 [US1] 前端：`components/GlobalSearch.tsx`（Header 搜索框：防抖 300ms AutoComplete 下拉分组 + 回车 /search?q=）。（依赖 T006）
- [x] T008 [US1] 前端：`pages/search/SearchResultPage.tsx`（结果页：Tabs 分组 + 关键字高亮 + 分页）；App.tsx Header 集成 + 路由 /search。（依赖 T007）

## Phase 4: 验证与收尾

- [x] T009 后端：`mvn test` 全量通过。
- [x] T010 前端：`pnpm run typecheck` + `lint` + `test` 全量通过。
- [x] T011 [P] 手动冒烟：Header 输关键字 → 下拉分组结果 → 点击跳转 → 回车结果页。

## Dependencies & Execution Order

- T001/T002 可并行，均红阶段。
- T003 无依赖；T004 依赖 T003；T005 依赖 T004。
- T006 无依赖；T007 依赖 T006；T008 依赖 T007。
- Phase 4 完成后收尾。

## Notes

- LIKE 匹配 + 多关键字 AND；不引 ES。
- 数据权限：ADMIN 全量/其他按创建人。
- 下拉 Top 5/实体；结果页 20/页。
