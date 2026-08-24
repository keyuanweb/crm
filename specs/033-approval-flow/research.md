# 研究：审批流引擎

## R1 流程定义

**决策**: `approval_flow` 表存定义：
- business_type（CONTRACT/QUOTE）
- nodes JSON：`[{ "name":"销售经理审批", "approverType":"ROLE|USER|MANAGER", "approverValue":"SALES_MANAGER 或 userId" }]`
- condition JSON：`{ "field":"amount", "op":"GT", "value":100000, "extraNodes":[节点] }`（金额超阈值追加节点）
- enabled

## R2 实例/任务状态机

**决策**:
- instance: PENDING（审批中）/ APPROVED（全部通过）/ REJECTED（任一驳回）/ CANCELED
- task: PENDING / APPROVED / REJECTED / TRANSFERRED
- 发起：按条件解析节点序列 → 生成各节点 task（首节点 PENDING，其余 WAIT 用 PENDING + 仅当前节点可操作）
- 通过：当前 task APPROVED → 有下一节点则激活，无则 instance APPROVED
- 驳回：当前 task REJECTED → instance REJECTED → 通知发起人
- 重提：发起人调 renew → 新 instance（旧标 CANCELED）

## R3 并发校验

**决策**: 操作 task 时校验 status=PENDING（乐观：UPDATE WHERE status=PENDING，影响行 0 则冲突拒绝），防双审批人同时操作。

## R4 通知

**决策**: 任务激活/驳回用 NotificationService（026）推送审批人/发起人（entityType=APPROVAL + 跳转 /approvals）。

## R5 业务接入

**决策**: ContractService.submit/approve、QuoteService.submit/approve 改走引擎：存在启用 flow → 发起实例；否则原逻辑（兼容）。审批结果回调更新业务状态（APPROVED→合同 PENDING_EFFECTIVE 等）。

## R6 前端

**决策**: 审批流配置页（系统管理：定义 CRUD + 节点编辑器 + 条件）；审批中心（我的待办/已办列表 + 操作弹窗 + 详情时间线）。
