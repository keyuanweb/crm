# 契约：统计 /stats（006 扩展）

**Base**: `/api/v1/stats`（权限：ADMIN + SALES，见 README 矩阵）

> 本文档为 `specs/001-crm-core/contracts/stats.md` 的 006 模块扩展。既有 `GET /stats/opportunity-pipeline` 保持不动。

## GET /stats/dashboard

销售仪表盘聚合数据（FR-D01~D09）。

**Query**: 无（固定当月口径；`month` 可选，格式 `YYYY-MM`，默认当月）。

**Response 200**

```json
{
  "summary": {
    "opportunityCount": 10,
    "amountTotal": 2000000,
    "winRate": 0.667,
    "customerCount": 8,
    "activeCustomerCount": 7,
    "newCustomersThisMonth": 2
  },
  "funnel": {
    "stages": [
      { "stage": "INITIAL_CONTACT", "count": 4, "amountTotal": 400000, "conversionRate": null },
      { "stage": "NEGOTIATING",     "count": 3, "amountTotal": 900000, "conversionRate": 0.75 },
      { "stage": "CLOSED_WON",      "count": 2, "amountTotal": 500000, "conversionRate": 0.667 },
      { "stage": "CLOSED_LOST",     "count": 1, "amountTotal": 200000, "conversionRate": null }
    ],
    "grandTotal": { "count": 10, "amountTotal": 2000000 }
  },
  "forecast": {
    "weightedAmount": 830000,
    "breakdown": [
      { "stage": "INITIAL_CONTACT", "amount": 400000, "probability": 0.2, "weighted": 80000 },
      { "stage": "NEGOTIATING", "amount": 900000, "probability": 0.5, "weighted": 450000 },
      { "stage": "CLOSED_WON", "amount": 500000, "probability": 1.0, "weighted": 500000 }
    ]
  },
  "performance": {
    "month": "2026-08",
    "targetAmount": 1000000,
    "wonAmount": 500000,
    "achievementRate": 0.5,
    "configured": true
  },
  "followUps": {
    "total": 12,
    "byMethod": [
      { "method": "PHONE", "count": 6 },
      { "method": "EMAIL", "count": 4 },
      { "method": "MEETING", "count": 2 }
    ],
    "recent": [
      { "id": 101, "method": "PHONE", "content": "沟通续约意向", "customerName": "Acme 科技", "followUpBy": "销售员", "createdAt": "2026-08-22T10:00:00" }
    ]
  },
  "stalledOpportunities": [
    { "id": 7, "opportunityName": "CRM 采购", "customerName": "Acme 科技", "amount": 300000, "stage": "NEGOTIATING", "stalledDays": 12, "lastUpdatedAt": "2026-08-10T09:00:00" }
  ],
  "generatedAt": "2026-08-22T12:00:00"
}
```

**规则**:
- 汇总口径 = `deleted = 0`；漏斗/预测基于全部销售机会（含终态）；
- `conversionRate` = 后一阶段数量 / 前一阶段数量（首阶段与终态后为 null）；
- `winRate` = CLOSED_WON / (CLOSED_WON + CLOSED_LOST)，无已关闭机会时为 0；
- 预测概率固定：INITIAL_CONTACT 0.2 / NEGOTIATING 0.5 / CLOSED_WON 1.0 / CLOSED_LOST 0.0；
- `performance.configured=false` 时 targetAmount/achievementRate 为 null（未设目标）；
- 停滞预警阈值默认 7 天（`crm.stats.stalled-days`），仅活跃阶段（INITIAL_CONTACT/NEGOTIATING）；
- 结果缓存 Redis 5 分钟；写操作（销售机会/客户/跟进/目标）后失效；
- 金额单位为分，前端展示换算。

**错误**: 401（未认证）/ 403（SUPPORT 角色不可见）。

## GET /stats/sales-targets

查询某月销售目标（FR-D04，查询部分）。

**Query**: `month`（必填，`YYYY-MM`）。

**Response 200**

```json
{
  "month": "2026-08",
  "targetAmount": 1000000,
  "createdBy": 1,
  "updatedAt": "2026-08-22T09:00:00"
}
```

**规则**: 未设置目标时返回 `targetAmount: null`（200，非 404）。

## PUT /stats/sales-targets

设置/更新某月销售目标（FR-D04 设置部分，upsert 语义；仅 ADMIN）。

**Body**:

```json
{ "month": "2026-08", "targetAmount": 1000000 }
```

**Response 200**（同 GET 响应结构）。

**校验**: `month` 格式 `YYYY-MM`；`targetAmount ≥ 0`。

**错误**: 400（格式错误）/ 401 / 403（非 ADMIN）。

## 权限矩阵（更新）

| 端点 | ADMIN | SALES | SUPPORT |
|---|---|---|---|
| GET /stats/opportunity-pipeline | ✅ | ✅ | ❌ |
| GET /stats/dashboard | ✅ | ✅ | ❌ |
| GET /stats/sales-targets | ✅ | ✅ | ❌ |
| PUT /stats/sales-targets | ✅ | ❌ | ❌ |
