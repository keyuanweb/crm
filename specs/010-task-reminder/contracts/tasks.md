# 契约：任务 /tasks

**Base**: `/api/v1/tasks`（所有登录用户：ADMIN + SALES + SUPPORT；数据隔离：仅本人任务）

## GET /tasks

当前用户任务分页列表（FR-T01）。

**Query**: `keyword`（标题）、`status`（TODO/DONE）、`priority`、`linkedType`、`page`、`pageSize`。

**Response 200**

```json
{
  "items": [
    { "id": 1, "title": "跟进 Acme 科技", "dueAt": "2026-08-20T10:00:00",
      "priority": "HIGH", "status": "TODO", "linkedType": "CUSTOMER", "linkedId": 2,
      "remark": null, "reminderStatus": "OVERDUE", "overdueDays": 2,
      "version": 0, "createdAt": "2026-08-18T09:00:00" }
  ],
  "total": 1, "page": 1, "pageSize": 20
}
```

**规则**: 仅返回 `owner_id = 当前用户` 的任务；reminderStatus 动态计算（OVERDUE/TODAY/NORMAL/DONE）。

## POST /tasks

创建任务（FR-T02）。

**Body**:

```json
{ "title": "跟进 Acme 科技", "dueAt": "2026-08-25T10:00:00",
  "priority": "MEDIUM", "linkedType": "CUSTOMER", "linkedId": 2, "remark": null }
```

**Response 201**: 任务结构。

**校验**: title 必填；priority ∈ HIGH/MEDIUM/LOW（默认 MEDIUM）；linkedType 若填 ∈ CUSTOMER/LEAD/CONTRACT/ORDER；owner=当前用户。

## PUT /tasks/{id}

编辑任务（FR-T03）。

**Body**: 同 POST（含 `version`）。

**Response 200**。

**错误**: 404 TASK_NOT_FOUND / 403（非本人）/ 409 VERSION_CONFLICT。

## POST /tasks/{id}/toggle

完成任务或重新打开（FR-T04，TODO↔DONE）。

**Body**: 无。

**Response 200**: 更新后结构。

## DELETE /tasks/{id}

逻辑删除任务（FR-T05）。

**Response 200**。

## GET /tasks/reminder-summary

提醒汇总（FR-T07）：逾期数与今日到期数（仅当前用户）。

**Response 200**

```json
{ "overdueCount": 2, "todayCount": 1 }
```

## GET /tasks/calendar

按月日历数据（FR-T08）。

**Query**: `month`（必填，`YYYY-MM`）。

**Response 200**

```json
{
  "month": "2026-08",
  "days": [
    { "date": "2026-08-20", "tasks": [ { "id": 1, "title": "跟进 Acme 科技", "status": "TODO", "priority": "HIGH", "reminderStatus": "OVERDUE" } ] }
  ]
}
```

## 错误码汇总

| code | status | 含义 |
|---|---|---|
| TASK_NOT_FOUND | 404 | 任务不存在 |
| VERSION_CONFLICT | 409 | 乐观锁冲突 |
| FORBIDDEN | 403 | 非本人任务 |

## 跟进自动建任务（复用 POST /follow-ups）

`POST /follow-ups` 请求体新增可选字段：

```json
{ "customerId": 2, "method": "PHONE", "content": "沟通", "nextFollowUpAt": "2026-08-25T10:00:00", "createTask": true }
```

`createTask=true` 且 `nextFollowUpAt` 非空 → 自动创建任务（title="跟进：客户名/线索名"，due_at=nextFollowUpAt，linkedType 依 customerId/leadId）。
