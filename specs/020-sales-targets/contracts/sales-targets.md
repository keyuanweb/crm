# 契约：团队销售目标与排行 sales-targets

**Base**: `/api/v1/stats`

## GET /stats/sales-targets

查询销售目标（个人或全局）。

**Query**: `month`（必填，YYYY-MM）、`userId`（可选；不传或传空查全局目标，传 userId 查该销售个人目标）。

**Response 200**

```json
{ "success": true, "data": { "month": "2026-08", "targetAmount": 1000000, "userId": 5, "createdBy": 1, "updatedAt": "..." } }
```

- 未设置目标：`targetAmount` 为 null（非 404）。
- `userId` 为 null 表示全局目标。

## PUT /stats/sales-targets

设置/更新目标（个人或全局）。

**Body**:

```json
{ "month": "2026-08", "targetAmount": 1000000, "userId": 5 }
```

- `userId` 为空：设置全局目标（兼容 006）；非空：设置该销售个人目标。
- 按 (userId, month) upsert。

## GET /stats/leaderboard

团队排行（按月）。

**Query**: `month`（默认当月，YYYY-MM）、`sortBy`（可选：rate=达成率降序[默认]/amount=赢单金额降序）。

**Response 200**

```json
{
  "success": true,
  "data": {
    "items": [
      { "userId": 5, "displayName": "张三", "targetAmount": 1000000, "wonAmount": 800000, "achievementRate": 0.8 },
      { "userId": 6, "displayName": "李四", "targetAmount": null, "wonAmount": 200000, "achievementRate": null }
    ],
    "month": "2026-08"
  },
  "error": null
}
```

- 达成率 = wonAmount / targetAmount（0-1 或 null）。
- 无目标销售：targetAmount/achievementRate 为 null，排在最后。
- **权限**: ADMIN 全量；SALES 仅返回本人记录（012 数据权限）。

## GET /stats/dashboard（扩展）

`data.performance` 优先返回当前用户个人目标与达成率；个人未设置时回退全局目标。

```json
{ "performance": { "month": "2026-08", "targetAmount": 1000000, "wonAmount": 800000, "achievementRate": 0.8, "configured": true, "personal": true } }
```

- `personal: true` 表示个人目标；false 表示全局目标回退。

## 备注

- 金额单位为分。
- 赢单金额按 sales_opportunity.created_by 归属当月 CLOSED_WON。
