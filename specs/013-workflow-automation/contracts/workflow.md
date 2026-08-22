# 契约：工作流自动化 /workflows

**Base**: `/api/v1/workflows`（仅 ADMIN）

## GET /workflows/rules

规则分页列表（FR-W01）。

**Query**: `keyword`、`eventType`、`enabled`、`page`、`pageSize`。

**Response 200**

```json
{
  "items": [
    { "id": 1, "name": "商机谈判中创建任务", "eventType": "OPPORTUNITY_STAGE_CHANGED",
      "condition": { "field": "stage", "value": "NEGOTIATING" },
      "actionType": "CREATE_TASK", "action": { "titleTemplate": "跟进商机{name}", "dueDays": 3 },
      "enabled": true, "version": 0, "createdAt": "2026-08-22T10:00:00" }
  ],
  "total": 1, "page": 1, "pageSize": 20
}
```

## POST /workflows/rules

创建规则。

**Body**:

```json
{
  "name": "商机谈判中创建任务",
  "eventType": "OPPORTUNITY_STAGE_CHANGED",
  "condition": { "field": "stage", "value": "NEGOTIATING" },
  "actionType": "CREATE_TASK",
  "action": { "titleTemplate": "跟进商机{name}", "dueDays": 3 },
  "enabled": true
}
```

**Response 201**: 规则结构。

**校验**: name 必填；eventType ∈ 枚举；actionType ∈ 枚举；action JSON 字段按类型校验（ASSIGN 需 targetUserId；CREATE_TASK 需 titleTemplate/dueDays）。

## PUT /workflows/rules/{id}

编辑规则（含 version）。

## POST /workflows/rules/{id}/toggle

启停规则（FR-W02）。

## DELETE /workflows/rules/{id}

逻辑删除规则。

## GET /workflows/logs

执行日志分页（FR-W07）。

**Query**: `ruleId`、`eventType`、`success`、`page`、`pageSize`。

**Response 200**

```json
{
  "items": [
    { "id": 10, "ruleId": 1, "ruleName": "商机谈判中创建任务", "eventType": "OPPORTUNITY_STAGE_CHANGED",
      "entityType": "SALES_OPPORTUNITY", "entityId": 5, "matched": true,
      "actionResult": "已创建任务 #12", "success": true, "createdAt": "2026-08-22T11:00:00" }
  ],
  "total": 1, "page": 1, "pageSize": 20
}
```

## GET /workflows/notifications

当前用户站内通知列表（NOTIFY 动作产出）。

**Query**: `page`、`pageSize`。

## POST /workflows/notifications/{id}/read

标记通知已读。

## 错误码汇总

| code | status | 含义 |
|---|---|---|
| WORKFLOW_RULE_NOT_FOUND | 404 | 规则不存在 |
| WORKFLOW_INVALID_ACTION | 400 | 动作配置不合法 |

## 权限矩阵

| 端点 | ADMIN | SALES | SUPPORT |
|---|---|---|---|
| GET/POST/PUT/DELETE /workflows/rules、toggle | ✅ | ❌ | ❌ |
| GET /workflows/logs | ✅ | ❌ | ❌ |
| GET /workflows/notifications、POST /read | ✅ | ✅ | ✅ |
