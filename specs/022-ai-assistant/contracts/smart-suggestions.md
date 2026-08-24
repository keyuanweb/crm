# 契约：智能建议 smart-suggestions

**Base**: `/api/v1/suggestions`（SALES 仅本人数据，ADMIN 全量）

## GET /suggestions

智能建议列表（按优先级排序，上限 20 条）。

**Response 200**

```json
{
  "success": true,
  "data": {
    "items": [
      { "type": "CUSTOMER_AT_RISK", "title": "跟进客户：Acme 科技",
        "reason": "已 53 天无跟进且无新订单，健康度 35", "priority": "URGENT",
        "entityType": "CUSTOMER", "entityId": 3, "action": "follow_up" }
    ],
    "total": 1
  },
  "error": null
}
```

- `priority`: URGENT / IMPORTANT / NORMAL。
- `action`: follow_up / push / process（前端映射跳转路径）。

## POST /suggestions/{type}/{entityId}/ignore

将某条建议标记为已忽略（当前用户）。

**Response 200**: `{ "success": true }`

- 忽略后该实体对应建议不再出现（TTL 90 天）。

## GET /suggestions/summary

建议摘要计数（首页卡片）。

**Response 200**

```json
{
  "success": true,
  "data": { "atRiskCustomers": 1, "stalledOpportunities": 2, "followUpCustomers": 3, "highScoreLeads": 4 },
  "error": null
}
```

## 备注

- 建议类型：CUSTOMER_AT_RISK / OPPORTUNITY_STALLED / CUSTOMER_FOLLOWUP / LEAD_HIGH_SCORE。
- 去重：同实体多规则合并取最高优先级。
- 数据权限：SALES 仅见可访问实体。
