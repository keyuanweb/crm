# 契约：KPI 大屏 kpi-board

**Base**: `/api/v1/stats/kpi-board`（仅 ADMIN）

## GET /stats/kpi-board

大屏聚合数据（一次返回全部区块）。

**Response 200**

```json
{
  "success": true,
  "data": {
    "kpi": { "opportunityCount": 5, "amountTotal": 8150000, "winRate": 0.4,
             "customerCount": 20, "activeCustomerCount": 18, "newCustomersThisMonth": 5 },
    "funnel": { "stages": [ { "stage": "INITIAL_CONTACT", "count": 3, "amountTotal": 4000000, "conversionRate": null } ],
                "grandTotal": { "stage": "TOTAL", "count": 5, "amountTotal": 8150000, "conversionRate": null } },
    "leaderboard": [ { "userId": 5, "displayName": "张三", "targetAmount": 1000000,
                        "wonAmount": 800000, "achievementRate": 0.8 } ],
    "healthDistribution": { "red": 3, "yellow": 5, "green": 12 },
    "suggestions": { "atRiskCustomers": 1, "stalledOpportunities": 2, "followUpCustomers": 3, "highScoreLeads": 4 },
    "trend": [ { "date": "2026-08-01", "count": 2, "amount": 300000 } ]
  },
  "error": null
}
```

**权限**: 仅 ADMIN（`@PreAuthorize("hasRole('ADMIN')")`）。

**缓存**: 后端 Redis 缓存 5 分钟（`stats:kpi-board`）。

## 备注

- 金额单位为分。
- 排行默认 TopN=10。
- 趋势为近 30 天按日。
