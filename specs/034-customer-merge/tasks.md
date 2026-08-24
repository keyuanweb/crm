# Tasks: 客户查重合并

**Input**: Design documents from `/specs/034-customer-merge/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/customer-merge.md

**Tests**: 章程原则四要求测试先于实现（红→绿），本功能含后端单元/集成 + 前端测试。

## Phase 1: 后端测试先行（TDD 红）

- [x] T001 [P] [US1] 后端：`CustomerMergeServiceTest`——名称归一化重复检出（相同 100/包含 90）、电话重复 85、关联计数、合并转移（订单/商机/联系人/跟进/工单）、字段冲突主优先、从记录回收站。红阶段。
- [x] T002 [P] [US1] 后端：`integration/MergeIT.java`——建两个相似客户→扫描检出→合并→关联数据转移+从记录回收站。红阶段。

## Phase 2: 后端实现

- [x] T003 [US1] 后端：DTO（DuplicateGroupResponse/DuplicateItem/MergeRequest）。
- [x] T004 [US1] 后端：`CustomerMergeService`——查重扫描（名称归一化分组 + 电话/邮箱精确 + 关联计数）+ 合并（单事务：关联表 customer_id 转移 + 字段冲突主优先 + 从记录回收站 025 + 审计）。（依赖 T003 + 各关联 Mapper + RecycleBinService）
- [x] T005 [US1] 后端：`CustomerMergeController`——GET /api/v1/customers/duplicates + POST /api/v1/customers/merge（customer:merge 权限）。（依赖 T004）

## Phase 3: 前端

- [x] T006 [P] [US1] 前端：`types/merge.ts` + `customerMergeService.ts`。
- [x] T007 [US1] 前端：`DuplicateMergePage`（查重合并页：扫描按钮 + 重复对列表（相似度/关联计数）+ 合并确认弹窗）。（依赖 T006）
- [x] T008 [US1] 前端：App.tsx 路由注册（客户管理分组）。（依赖 T007）

## Phase 4: 验证与收尾

- [x] T009 后端：`mvn test` 全量通过。
- [x] T010 前端：`pnpm run typecheck` + `lint` + `test` 全量通过。
- [x] T011 [P] 手动冒烟：建两个相似客户→扫描检出→合并→回收站可见。

## Dependencies & Execution Order

- T001/T002 可并行，均红阶段。
- T003 无依赖；T004 依赖 T003；T005 依赖 T004。
- T006 无依赖；T007 依赖 T006；T008 依赖 T007。
- Phase 4 完成后收尾。

## Notes

- 查重：名称归一化（去空格/大小写/全半角）+ 电话/邮箱精确。
- 合并单事务：转移 → 冲突主优先 → 回收站 → 审计。
- 关联表：sales_order/opportunity/contact/follow_up/ticket/customer_tag/customer_share。
