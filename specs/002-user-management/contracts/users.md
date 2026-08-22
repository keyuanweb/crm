# REST API 契约：用户管理（002-user-management）

**Branch**: `002-user-management` | **Date**: 2026-08-22 | **Spec**: [spec.md](../spec.md)

> 本目录是 002 端点契约的权威文档源（章程原则一：契约先于实现）。
> 通用约定（Base URL / 认证 / 分页信封 / 错误格式 / 状态码）沿用 `specs/001-crm-core/contracts/README.md`。
> 后端以 springdoc-openapi 暴露同一契约；前端 `types/user.ts`、`services/userService.ts` 按此对齐。

## 角色权限

| 端点 | ADMIN | SALES | SUPPORT |
|---|:-:|:-:|:-:|
| GET/POST /users、GET/PUT /users/{id}、PUT /users/{id}/password | ✅ | ❌ | ❌ |
| PUT /users/me/password（本人改密） | ✅ | ✅ | ✅ |
| GET /audit-logs（审计日志查询，001 FR-017） | ✅ | ❌ | ❌ |

## 端点

### GET /users

分页查询用户列表（FR-001）。

**Query**: `page`, `pageSize`, `keyword`（对 username/displayName 模糊匹配）, `role`（ADMIN/SALES/SUPPORT，可选）

**Response 200**（分页信封）

```json
{
  "items": [
    { "id": 1, "username": "admin", "displayName": "系统管理员", "role": "ADMIN",
      "enabled": true, "lastLoginAt": "2026-08-22T09:00:00", "version": 1, "createdAt": "2026-08-21T10:00:00" }
  ],
  "total": 1, "page": 1, "pageSize": 20
}
```

### GET /users/{id}

用户详情（FR-002，含最后登录时间）。

**Response 200**: 同列表项结构。**错误**: 404 `USER_NOT_FOUND`。

### POST /users

创建用户（FR-003，仅 ADMIN）。

**Request**

```json
{
  "username": "sales01",
  "displayName": "销售一",
  "role": "SALES",
  "password": "pass1234"
}
```

**Response**: 201，返回用户对象。**校验**：username 3~50 位字母/数字/下划线且全局唯一（409 `USER_DUPLICATE`）；password 8~64 位且同时含字母与数字（400）；role ∈ {ADMIN, SALES, SUPPORT}。

### PUT /users/{id}

编辑用户（FR-004，仅 ADMIN）：显示名、角色、启停。

**Request**

```json
{ "displayName": "销售一", "role": "SUPPORT", "enabled": false, "version": 0 }
```

**Response**: 200 更新后对象。**规则**（FR-007）：不能停用当前登录账号（403）；系统必须至少保留一个启用 ADMIN（403）；版本不符 409 `VERSION_CONFLICT`。

### PUT /users/{id}/password

管理员重置密码（FR-005，仅 ADMIN）：重置后该用户旧访问令牌立即失效（token_version 递增）。

**Request**

```json
{ "newPassword": "newPass456" }
```

**Response**: 200 `{ "success": true }`。

### PUT /users/me/password

登录用户修改自己的密码（FR-006，全员）。

**Request**

```json
{ "oldPassword": "oldPass123", "newPassword": "newPass456" }
```

**Response**: 200 `{ "success": true }`。**错误**: 401 `INVALID_CREDENTIALS`（旧密码不正确）；修改后旧令牌立即失效（前端需重新登录）。

### GET /audit-logs

审计日志分页查询（001 FR-017，仅 ADMIN）。

**Query**: `page`, `pageSize`, `action`（CREATE/UPDATE/DELETE/IMPORT/EXPORT/CLOSE/RESET_PASSWORD/CHANGE_PASSWORD）, `entityType`（CUSTOMER/OPPORTUNITY/SALES_OPPORTUNITY/USER）, `actorName`

**Response 200**（分页信封，`items[].createdAt` 为 ISO 时间）

```json
{
  "items": [
    { "id": 1, "actorId": 1, "actorName": "admin", "action": "CREATE", "entityType": "CUSTOMER",
      "entityId": 42, "detail": "创建客户：张三", "createdAt": "2026-08-22T09:00:00" }
  ],
  "total": 1, "page": 1, "pageSize": 20
}
```

**错误**: 403（非 ADMIN）。
