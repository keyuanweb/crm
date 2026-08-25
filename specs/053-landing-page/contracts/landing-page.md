# 契约：托管落地页 /api/v1/landing-pages + /api/v1/public/lp

## 管理（仅 ADMIN）

### GET /landing-pages

落地页列表分页。**Query**: `keyword`、`page`、`pageSize`。

### POST /landing-pages

创建。**Body**

```json
{ "title": "夏季促销", "subtitle": "限时优惠", "description": "点击下方表单领取",
  "themeColor": "#1677ff", "formId": 3, "enabled": true }
```

**Response 201**: 落地页结构（含 id/updatedAt）。

### PUT /landing-pages/{id} / DELETE /landing-pages/{id}

编辑 / 删除（逻辑删除）。

## 公开访问（无需登录）

### GET /api/v1/public/lp/{id}

落地页渲染数据。

**Response 200**

```json
{
  "id": 1, "title": "夏季促销", "subtitle": "限时优惠", "description": "...",
  "themeColor": "#1677ff", "enabled": true,
  "form": { "id": 3, "fields": "[{\"name\":\"phone\",\"label\":\"手机号\"}]", "successMessage": "提交成功" }
}
```

**规则**: 落地页或关联表单停用 → 404 LANDING_PAGE_UNAVAILABLE。

### POST /api/v1/public/forms/{id}/submit（复用 036，UTM 扩展）

表单提交 URL 可带 `?utm_source=...&utm_campaign=...`，提交时捕获存入快照（响应不变）。

## UTM 统计（ADMIN）

### GET /landing-pages/{id}/stats?from&to

**Response 200**

```json
{
  "landingPageId": 1, "total": 15, "from": "2026-08-01", "to": "2026-08-25",
  "bySource": [ { "dimension": "facebook", "count": 8 }, { "dimension": "google", "count": 7 } ],
  "byCampaign": [ { "dimension": "summer", "count": 10 } ]
}
```

## 错误码

| code | status | 含义 |
|---|---|---|
| LANDING_PAGE_NOT_FOUND | 404 | 落地页不存在 |
| LANDING_PAGE_UNAVAILABLE | 404 | 落地页或关联表单已停用 |
| LANDING_FORM_INVALID | 422 | 关联表单不合法（不存在或已停用） |
