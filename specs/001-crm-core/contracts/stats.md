# 契约：统计 /stats

**Base**: `/api/v1/stats`（权限：ADMIN + SALES，见 README 矩阵）

## GET /stats/opportunity-pipeline

商机管道统计：按销售机会阶段汇总数量与金额（FR-011，用户故事 6）。

**Query**: 无（或可选 `customerId` 做单客户视角）。

**Response 200**

```json
{
  "stages": [
    { "stage": "INITIAL_CONTACT", "count": 4, "amountTotal": 400000 },
    { "stage": "NEGOTIATING",     "count": 3, "amountTotal": 900000 },
    { "stage": "CLOSED_WON",      "count": 2, "amountTotal": 500000 },
    { "stage": "CLOSED_LOST",     "count": 1, "amountTotal": 200000 }
  ],
  "grandTotal": { "count": 10, "amountTotal": 2000000 },
  "generatedAt": "2026-08-21T12:00:00"
}
```

**规则**:
- 汇总口径 = `deleted = 0` 的全部销售机会（含终态，SC-006 与列表一致）；
- 结果缓存 Redis 5 分钟（R3）；关闭/编辑销售机会后主动失效；
- 金额单位为分，前端展示时换算。

**错误**: 401（未认证）/ 403（SUPPORT 角色不可见）。
