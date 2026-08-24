# Tasks: 通用多级审批流

**Input**: Design documents from `/specs/033-approval-flow/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/approval-flow.md

**Tests**: 章程原则四要求测试先于实现（红→绿），本功能含后端单元/集成 + 前端测试。

## Phase 1: 基础设施

- [x] T001 [P] [US1] 后端：`db/migration/V49__approval_flow.sql`——approval_flow/approval_instance/approval_task/approval_log 四表。
- [x] T002 [P] [US1] 后端：实体 4 个 + Mapper 4 个。

## Phase 2: 后端测试先行（TDD 红）

- [x] T003 [P] [US1] 后端：`ApprovalEngineServiceTest`——发起生成多任务、通过推进、全通过 instance APPROVED、驳回 REJECTED、重提新实例、并发冲突。红阶段。
- [x] T004 [P] [US1] 后端：`integration/ApprovalIT.java`——配置双级流程→发起→一级通过→二级驳回→重提→全通过。红阶段。

## Phase 3: 后端实现

- [x] T005 [US1] 后端：DTO（FlowRequest/FlowResponse/TaskActionRequest/InstanceResponse）+ 实体映射。
- [x] T006 [US1] 后端：`ApprovalFlowService`——定义 CRUD + 条件分支解析（金额阈值追加节点）。（依赖 T002）
- [x] T007 [US1] 后端：`ApprovalEngineService`——发起（按流程生成任务序列）/通过/驳回/转交/重提 + 状态机（乐观并发校验）+ 026 通知 + 日志。（依赖 T005/T006）
- [x] T008 [US1] 后端：`ApprovalController`——/api/v1/approval-flows（配置 CRUD）+ /api/v1/approvals（todos/done/detail/approve/reject/transfer/renew）。（依赖 T006/T007）
- [x] T009 [US1] 后端：合同/报价提交审批接入引擎（存在启用 flow 走引擎，否则原逻辑；审批全通过回调更新业务状态）。（依赖 T007）

## Phase 4: 前端

- [x] T010 [P] [US1] 前端：`types/approval.ts` + `approvalService.ts`。
- [x] T011 [US1] 前端：`ApprovalFlowPage`（审批流配置：定义 CRUD + 节点编辑器 + 金额条件）。（依赖 T010）
- [x] T012 [US1] 前端：`ApprovalCenterPage`（我的审批：待办/已办 + 通过/驳回/转交弹窗 + 详情时间线）；App.tsx 路由。（依赖 T011）

## Phase 5: 验证与收尾

- [x] T013 后端：`mvn test` 全量通过；H2 schema 同步新表。
- [x] T014 前端：`pnpm run typecheck` + `lint` + `test` 全量通过。
- [x] T015 [P] 手动冒烟：配置双级流程→合同提交→一级通过→二级驳回→重提→全通过。

## Dependencies & Execution Order

- T001/T002 可并行。
- T003/T004 可并行，均红阶段；依赖 T001/T002。
- T005 依赖 T002；T006 依赖 T005；T007 依赖 T006；T008 依赖 T007；T009 依赖 T007。
- T010 无依赖；T011 依赖 T010；T012 依赖 T011。
- Phase 5 完成后收尾。

## Notes

- 状态机：instance PENDING/APPROVED/REJECTED/CANCELED；task PENDING/APPROVED/REJECTED/TRANSFERRED。
- 并发：task 操作 UPDATE WHERE status=PENDING 乐观校验。
- 条件分支基于金额字段；审批人类型 ROLE/USER/MANAGER。
- 复用 026 通知；合同/报价兼容原逻辑。
