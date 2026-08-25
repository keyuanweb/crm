# 契约：邮件高级能力 /api/v1/email + /api/v1/public/email

## 公开退订（无需登录）

### POST /api/v1/public/email/unsubscribe

邮件退订链接调用。**Body**: `{ "email": "customer@test.com" }`。**Response 200**: 退订成功（重复退订幂等）。

## 退订管理（ADMIN + SALES）

### GET /api/v1/email/unsubscribes

退订名单分页。**Query**: `keyword`（邮箱）、`page`、`pageSize`。

**Response 200**

```json
{
  "items": [
    { "id": 1, "email": "customer@test.com", "campaignId": 3, "unsubscribedAt": "2026-08-25T10:00:00" }
  ],
  "total": 1, "page": 1, "pageSize": 20
}
```

### DELETE /api/v1/email/unsubscribes/{id}

恢复（取消退订）。**Response 200**。

## 群发统计（ADMIN + SALES）

### GET /api/v1/email/campaigns/{id}/stats

**Response 200**

```json
{
  "campaignId": 3, "total": 100, "sent": 98, "failed": 2,
  "openCount": 40, "clickCount": 15,
  "openRate": 40.8, "clickRate": 15.3,
  "variant": "AB", "winner": "A",
  "variantStats": [
    { "variant": "A", "sent": 49, "openCount": 22, "openRate": 44.9 },
    { "variant": "B", "sent": 49, "openCount": 18, "openRate": 36.7 }
  ]
}
```

- variant=AB 时返回 variantStats 与 winner。

## 创建群发 A/B（扩展 030 契约）

- POST /api/v1/email/campaigns Body 增加 `variant`（NONE/AB）、`subjectB`（variant=AB 时必填）。

## 错误码

| code | status | 含义 |
|---|---|---|
| EMAIL_UNSUBSCRIBE_EMAIL_REQUIRED | 422 | 邮箱不能为空 |
| EMAIL_AB_SUBJECT_REQUIRED | 422 | A/B 测试需提供 B 主题 |
