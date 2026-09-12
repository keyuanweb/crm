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

## 授权语义（2026-09-12 变更：083-engineering-consolidation）

> 本章为**既有契约的修订**，不是新契约。按章程原则一"契约不得被静默修改"，`083` 对本文件所辖端点的授权行为变更记在此处。

### 密钥主体的角色不再是 ADMIN（FR-G11）

`X-API-Key` 通过后注入的主体从 `role = "ADMIN"`（写死）改为 `role = "OPEN_API"` 的**机器主体**：

| | 改造前 | 改造后 |
|---|---|---|
| 主体标识 | `userId = 0`，`name = "open-api"` | `userId = 密钥的创建者`，`name = "open-api"`，`role = "OPEN_API"` |
| 角色 | `ADMIN` | `OPEN_API` |
| 行级数据范围 | **不施加**（`EntityAccessService.isUnrestricted()` 对 ADMIN 短路） | 按密钥创建者的可见范围 |
| 操作级权限 | **不检查**（`PermissionAspect` 对 ADMIN 短路） | 按角色权限矩阵检查 |

**这是行为变更，不是缺陷**：原先任何一把有效密钥都同时短路了行级数据范围与操作级权限两条独立机制，等价于"持钥即全量"。改造后调用方按密钥创建者的身份受同样的授权约束。

**调用方须预期的结果**：原先**因为 ADMIN 角色**而能读到全量数据的调用方，现在可能收到 **403**，或只读到其创建者可见范围内的行（结果集变小、不为空）。这是预期效果，不应作为故障处理。若某集成确实需要更宽的数据范围，应通过扩大该密钥创建者的数据权限解决，而不是恢复 ADMIN 角色。

`POST /api/v1/open/leads` 所需的 `lead:write` 等 **scope 校验不变**——scope 回答"这把密钥能调哪些开放端点"，角色回答"以谁的身份读数据"，两者是不同层次的判定。

### Webhook 回调地址的接受范围收窄（FR-G13）

`POST /api/v1/platform/webhooks` 的 `callbackUrl` 由"不校验"改为按出站策略校验：

- 仅接受 `http`/`https`；主机名须命中部署方配置的 `crm.outbound.allowed-hosts`（**默认全拒**）。
- 回环、私有、链路本地、保留网段（含云元数据地址 `169.254.169.254`）默认拒绝。
- 投递时**不自动跟随 3xx**：重定向的落点逐跳重新校验，超过 3 跳中止。

**调用方须预期的结果**：指向内网或本机地址的 `callbackUrl` 现在会被拒（`OPEN_WEBHOOK_URL_INVALID`）。需要回调到内网地址的部署，必须把该主机显式列入 `crm.outbound.allowed-hosts`。

## 错误码

| code | status | 含义 |
|---|---|---|
| OPEN_API_KEY_REQUIRED | 401 | 缺少 X-API-Key |
| OPEN_API_KEY_INVALID | 401 | API Key 无效/吊销/过期 |
| OPEN_API_SCOPE_DENIED | 403 | 权限范围不足 |
| OPEN_WEBHOOK_URL_INVALID | 422 | 回调 URL 不合法 |
| OPEN_EVENT_INVALID | 422 | 事件类型不合法 |
