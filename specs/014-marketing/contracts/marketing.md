# 契约：市场营销 /campaigns

**Base**: `/api/v1/campaigns`（写：ADMIN + SALES；查看：全员）

## GET /campaigns

活动分页列表（FR-M01/M02）。

**Query**: `keyword`（名称）、`channel`、`status`、`page`、`pageSize`。

**Response 200**

```json
{
  "items": [
    { "id": 1, "name": "春季广告投放", "channel": "AD", "budget": 100000,
      "cost": 80000, "startDate": "2026-03-01", "endDate": "2026-04-30",
      "status": "ENDED", "leadCount": 12, "customerCount": 3,
      "version": 0, "createdAt": "2026-02-20T10:00:00" }
  ],
  "total": 1, "page": 1, "pageSize": 20
}
```

## POST /campaigns

创建活动。

**Body**:

```json
{ "name": "秋季展会", "channel": "EXHIBITION", "budget": 200000, "cost": 150000,
  "startDate": "2026-09-01", "endDate": "2026-09-30" }
```

**Response 201**: 活动结构（status 默认 PLANNING）。

**校验**: name 必填；channel ∈ 枚举；budget/cost ≥0；endDate ≥ startDate。

## PUT /campaigns/{id}

编辑活动（含 version）。

## POST /campaigns/{id}/start

状态流转：PLANNING→RUNNING。

## POST /campaigns/{id}/end

状态流转：RUNNING→ENDED（ENDED 不可回退）。

## DELETE /campaigns/{id}

逻辑删除；有归因数据 → 409 CAMPAIGN_HAS_ATTRIBUTION。

## GET /campaigns/channel-roi

渠道 ROI 统计（FR-M06/M07）。按渠道聚合（排序 = 渠道首次出现顺序），成本为 0 时 ROI 为 null。

**Response 200**（`data` 为数组；无活动时为空数组）

```json
{
  "data": [
    { "channel": "AD", "campaignCount": 2, "totalCost": 160000,
      "leadCount": 20, "customerCount": 5, "conversionRate": 0.25,
      "estimatedRevenue": 900000, "roi": 5.625 }
  ]
}
```

## 归因字段（lead/customer）

线索/客户创建、编辑、列表、详情响应均含可选字段 `campaignId`（活动 id，无归因为 null）。线索转化时 `campaign_id` 自动带入客户。

**POST /leads** Body 示例：`{ "name": "...", "company": "...", "campaignId": 1 }`

## 错误码汇总

| code | status | 含义 |
|---|---|---|
| CAMPAIGN_NOT_FOUND | 404 | 活动不存在 |
| CAMPAIGN_INVALID_STATE | 409 | 状态流转非法 |
| CAMPAIGN_HAS_ATTRIBUTION | 409 | 活动存在归因数据，不可删除 |

## 权限矩阵

| 端点 | ADMIN | SALES | SUPPORT |
|---|---|---|---|
| GET /campaigns、GET /campaigns/channel-roi | ✅ | ✅ | ✅ |
| POST/PUT/DELETE /campaigns、start/end | ✅ | ✅ | ❌ |
