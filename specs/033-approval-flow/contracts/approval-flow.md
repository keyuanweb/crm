# 契约：审批流

**Base**: `/api/v1/approval-flows`（配置，workflow:manage）、`/api/v1/approvals`（我的审批）

## 流程定义

### GET /approval-flows?businessType=

流程列表：`{ "items": [ { "id":1, "name":"合同审批", "businessType":"CONTRACT", "nodes":"[...]", "conditionJson":"...", "enabled":true } ], "total": n }`

### POST /approval-flows

**Body**: `{ "name":"合同审批", "businessType":"CONTRACT", "nodes":[ { "name":"销售经理", "approverType":"ROLE", "approverValue":"SALES_MANAGER" } ], "conditionJson": { "field":"amount", "op":"GT", "value":100000, "extraNodes":[ { "name":"总经理", "approverType":"ROLE", "approverValue":"GENERAL_MANAGER" } ] }, "enabled":true }`

### PUT /approval-flows/{id} / DELETE /approval-flows/{id}

编辑/删除（删除仅未使用）。

## 审批执行

### GET /approvals/todos

我的待办（当前用户审批人的 PENDING 任务）。

### GET /approvals/done

我的已办（历史）。

### GET /approvals/{id}

实例详情（任务时间线 + 日志）。

### POST /approvals/{id}/tasks/{taskId}/approve

通过。**Body**: `{ "comment": "同意" }`

### POST /approvals/{id}/tasks/{taskId}/reject

驳回。**Body**: `{ "comment": "退回修改" }`

### POST /approvals/{id}/tasks/{taskId}/transfer

转交。**Body**: `{ "toUserId": 5 }`

### POST /approvals/{id}/renew

重提（发起人，驳回后新建实例）。

## 业务接入

- 合同/报价提交审批：POST /contracts/{id}/submit 等，存在启用 flow → 自动发起实例；否则原逻辑。
- 审批结果回调：全通过 → 业务状态推进（合同 APPROVED→生效待确认等）。

## 备注

- 状态机：PENDING→APPROVED/REJECTED；驳回→REJECTED 可重提。
- 通知复用 026（审批人/发起人）。
- 审批人类型 ROLE（角色码）/USER（用户 id）/MANAGER（发起人上级）。
