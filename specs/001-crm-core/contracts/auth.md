# 契约：认证 /auth

**Base**: `/api/v1/auth`

## POST /login

登录并获取令牌。

**Request**

```json
{
  "username": "admin",
  "password": "admin123"
}
```

**Response 200**

```json
{
  "accessToken": "<JWT, 30 分钟>",
  "refreshToken": "<JWT, 7 天>",
  "user": { "id": 1, "username": "admin", "displayName": "系统管理员", "role": "ADMIN" }
}
```

**错误**: 401 `INVALID_CREDENTIALS`（用户名或密码错误）。

## POST /refresh

用 refresh token 换取新的 access token。

**Request**

```json
{ "refreshToken": "<refreshToken>" }
```

**Response 200**

```json
{ "accessToken": "<新 JWT>" }
```

**错误**: 401 `REFRESH_TOKEN_INVALID`（过期、已登出黑名单）。

## POST /logout

注销当前会话：将 refresh token 加入 Redis 黑名单，使 access token 立即失效（R3）。

**Request**

```json
{ "refreshToken": "<refreshToken>" }
```

**Response**: 200 `{ "success": true }`

## GET /me

返回当前登录用户信息（前端初始化用户态）。

**Response 200**: 同 /login 的 `user` 结构。
