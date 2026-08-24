# 契约：客户 360 与健康度评分 customer-360

**Base**: `/api/v1/customers`（除预警接口外沿用客户既有权限）

## GET /customers/{id}（扩展）

现有客户详情接口，响应 `data` 新增以下字段（原字段不变，向后兼容）：

```json
{
  "success": true,
  "data": {
    "id": 1, "name": "Acme 科技", "company": "Acme Inc.",
    "contacts": [], "followUps": [], "opportunities": [],
    "orders": [
      { "id": 10, "orderNo": "SO-20260823-0001", "title": "采购订单", "amount": 1500000, "status": "PAID" }
    ],
    "paymentSummaries": [
      { "orderId": 10, "orderNo": "SO-20260823-0001", "totalPlan": 1500000, "paid": 1500000, "overdue": 0 }
    ],
    "contracts": [
      { "id": 5, "contractNo": "HT-20260823-0001", "title": "服务合同", "amount": 1500000, "status": "EFFECTIVE" }
    ],
    "tickets": [
      { "id": 8, "title": "登录问题", "priority": "HIGH", "status": "OPEN", "slaStatus": "NORMAL" }
    ],
    "amountSummary": { "totalOrder": 1500000, "paid": 1500000, "dueOverdue": 0 },
    "health": {
      "score": 82,
      "level": "GREEN",
      "deductions": [ { "dimension": "跟进活跃度", "deduct": 8 } ]
    }
  },
  "error": null
}
```

**权限**: 沿用 `CustomerService.checkViewPermission`（012 数据权限）。

## GET /customers/health/at-risk

流失预警列表：超过 N 天无跟进且无新订单的客户（N 默认 45，可配置），按健康度升序。

**Query**: `page`、`pageSize`、`daysInactive`（可选，覆盖默认 45）。

**Response 200**

```json
{
  "success": true,
  "data": {
    "items": [
      { "id": 3, "name": "Beta 公司", "company": "Beta Co.", "healthScore": 35,
        "lastFollowUpAt": "2026-07-01T10:00:00", "lastOrderAt": null, "daysInactive": 53,
        "ownerName": "张三" }
    ],
    "total": 1, "page": 1, "pageSize": 20
  },
  "error": null
}
```

**权限**: 仅返回当前用户可访问客户（012）。

## PUT /health-score-config（管理员，可选扩展）

调整评分维度权重/阈值。若本期不做配置 UI，可仅提供读取接口或延后；默认用种子配置。

## 备注

- 订单金额单位为分；`amountSummary` 中 `totalOrder` 为订单金额合计（分），`paid` 为已回款合计，`dueOverdue` 为逾期未回款合计。
- 健康度评分实时计算，不持久化。
- 跟进复用现有 `POST /follow-ups`（001 契约）。
