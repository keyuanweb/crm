# 契约：开放平台 /api/v1/open/** + 管理端点

## API Key 管理（仅 ADMIN）

### POST /api/v1/platform/api-keys

创建。**Body**: `{ "name": "数据同步", "scopes": ["customer:read","lead:write"], "expiresAt": null }`。

**Response 201**

```json
{
  "id": 1, "name": "数据同步", "key": "ck_a1b2c3d4...（完整值仅本次返回）",
  "keyPrefix": "ck_a1b2", "scopes": ["customer:read"], "status": "ACTIVE", "createdAt": "..."
}
```

### GET /api/v1/platform/api-keys

列表（仅前缀，无完整 key）。

### POST /api/v1/platform/api-keys/{id}/revoke

吊销。**Response 200**。

## Webhook 管理（仅 ADMIN）

### POST /api/v1/platform/webhooks

创建。**Body**: `{ "eventType": "LEAD_CREATED", "callbackUrl": "https://example.com/hook" }`。

**Response 201**: 含 secret（仅本次返回）。

### GET /api/v1/platform/webhooks / POST /api/v1/platform/webhooks/{id}/toggle / DELETE ...

订阅列表 / 启停 / 删除。

### GET /api/v1/platform/webhooks/{id}/deliveries

推送记录分页。

## 开放端点（X-API-Key 鉴权，无 JWT）

### GET /api/v1/open/customers

客户只读列表。**Header**: `X-API-Key: ck_...`。**Query**: `page`/`pageSize`/`keyword`。

### GET /api/v1/open/leads

线索只读列表。

### POST /api/v1/open/leads

创建线索（写入验证，需 lead:write 权限）。**Body**: name/company/email 等。

## 错误码

| code | status | 含义 |
|---|---|---|
| OPEN_API_KEY_REQUIRED | 401 | 缺少 X-API-Key |
| OPEN_API_KEY_INVALID | 401 | API Key 无效/吊销/过期 |
| OPEN_API_SCOPE_DENIED | 403 | 权限范围不足 |
| OPEN_WEBHOOK_URL_INVALID | 422 | 回调 URL 不合法 |
| OPEN_EVENT_INVALID | 422 | 事件类型不合法 |
