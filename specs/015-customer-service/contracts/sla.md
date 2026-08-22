# 契约：客户服务 - SLA 策略 /sla-policies

**Base**: `/api/v1/sla-policies`（仅 ADMIN）

## GET /sla-policies

策略列表（FR-C09）。

**Response 200**

```json
{
  "items": [
    { "id": 1, "priority": "HIGH", "respondHours": 4, "resolveHours": 24, "enabled": true,
      "version": 0, "createdAt": "2026-08-22T08:00:00" }
  ],
  "total": 4, "page": 1, "pageSize": 20
}
```

## POST /sla-policies

创建策略（每优先级唯一）。

**Body**:

```json
{ "priority": "URGENT", "respondHours": 2, "resolveHours": 8, "enabled": true }
```

**Response 201**: 策略结构。

**校验**: priority ∈ 枚举且不重复；respondHours/resolveHours ≥1 或 null（null=不约束）；至少配置一项时限。

## PUT /sla-policies/{id}

编辑策略（含 version）。

## DELETE /sla-policies/{id}

逻辑删除。

## GET /sla-policies/overview

SLA 超时统计（FR-C12）：未关闭工单中超时数/占比（按优先级分组）。

**Response 200**

```json
{
  "totalOpen": 20, "overdue": 3, "overdueRate": 0.15,
  "byPriority": [ { "priority": "HIGH", "totalOpen": 8, "overdue": 2, "overdueRate": 0.25 } ]
}
```

## 错误码

| code | status | 含义 |
|---|---|---|
| SLA_POLICY_NOT_FOUND | 404 | 策略不存在 |
| SLA_POLICY_DUPLICATE | 409 | 该优先级策略已存在 |
| SLA_POLICY_FORBIDDEN | 403 | 非 ADMIN |
