# Tasks: 发票管理

**Input**: Design documents from `/specs/038-invoice/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/invoice.md

**Tests**: 章程原则四要求测试先于实现（红→绿），本功能含后端单元/集成 + 前端测试。

## Phase 1: 基础设施

- [x] T001 [P] [US1] 后端：`db/migration/V53__invoice.sql`——invoice 表。
- [x] T002 [P] [US1] 后端：实体 Invoice + Mapper。
- [x] T003 [P] [US1] 后端：RoleConstants 权限字典加 `invoice:manage`。

## Phase 2: 后端测试先行（TDD 红）

- [x] T004 [P] [US1] 后端：`InvoiceServiceTest`——开票成功（编号生成）、金额超可开额拒绝、作废（原因必填 + 释放额度）、统计。红阶段。
- [x] T005 [P] [US1] 后端：`integration/InvoiceIT.java`——建订单→开票→列表→超可开额拒绝→作废→统计。红阶段。

## Phase 3: 后端实现

- [x] T006 [US1] 后端：DTO（InvoiceRequest/InvoiceResponse/InvoiceStatsResponse）。
- [x] T007 [US1] 后端：`InvoiceService`——开票（订单校验 + 剩余可开额 + 编号 INV-{yyyyMM}-{seq}）+ 作废（原因必填 + 审计 + 释放额度）+ 列表筛选 + 统计（按订单开票率）。（依赖 T002/T006 + SalesOrderMapper）
- [x] T008 [US1] 后端：`InvoiceController`——/api/v1/invoices（list/create/void/stats）。（依赖 T007）

## Phase 4: 前端

- [x] T009 [P] [US1] 前端：`types/invoice.ts` + `invoiceService.ts`。
- [x] T010 [US1] 前端：`InvoiceListPage`（发票列表：统计卡 + 订单/状态筛选 + 开票弹窗 + 作废弹窗）。（依赖 T009）
- [x] T011 [US1] 前端：App.tsx 路由注册（交易分组）。（依赖 T010）

## Phase 5: 验证与收尾

- [x] T012 后端：`mvn test` 全量通过；H2 schema 同步新表。
- [x] T013 前端：`pnpm run typecheck` + `lint` + `test` 全量通过。
- [x] T014 [P] 手动冒烟：开票→列表→超可开额拒绝→作废（原因）→统计。

## Dependencies & Execution Order

- T001/T002/T003 可并行。
- T004/T005 可并行，均红阶段；依赖 T001/T002。
- T006 依赖 T002；T007 依赖 T006；T008 依赖 T007。
- T009 无依赖；T010 依赖 T009；T011 依赖 T010。
- Phase 5 完成后收尾。

## Notes

- 累计开票（非作废）≤ 订单金额。
- 编号 INV-{yyyyMM}-{seq}（当日序号）。
- 作废需原因（必填）+ 审计；发票仅作废不删除。
- 权限 invoice:manage 入 028 字典。
