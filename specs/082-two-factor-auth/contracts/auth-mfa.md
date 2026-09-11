# API Contract: 双因素认证（Two-Factor Authentication / TOTP）

**创建日期**: 2026-09-11

## 基础信息

- **Base URL**: `/api/v1/auth/2fa`（管理端重置为 `/api/v1/admin/users/{id}/2fa`）
- **认证**: JWT Bearer Token（`/verify` 与登录二次验证除外，改用一次性 `mfaToken`）
- **内容类型**: `application/json`
- **错误信封**: 统一 `{ "code": "<ErrorCode>", "message": "<本地化描述>" }`，HTTP 状态码与 `code` 对齐（见下文错误码表）

---

## 端点列表

### 1. 生成绑定密钥（setup）

**POST** `/api/v1/auth/2fa/setup`

**鉴权**: 已登录 JWT。仅未启用 2FA 的用户可调用；已启用返回 `MFA_ALREADY_ENABLED`。

**请求体**（可选，用于密码二次确认）:
```json
{ "password": "admin123" }
```

**响应 200**:
```json
{
  "secret": "JBSWY3DPEHPK3PXP",
  "otpauthUrl": "otpauth://totp/CRM:admin?secret=JBSWY3DPEHPK3PXP&issuer=CRM&algorithm=SHA1&digits=6&period=30",
  "qrCodeDataUrl": "data:image/png;base64,iVBORw0KGgo...",
  "enabled": false
}
```

> 密钥（`secret`）仅在本响应中明文返回一次；落库仅存 AES-256-GCM 密文。调用本接口会生成新密钥并使上一未完成密钥失效。

**错误响应**:
- `401`: 未登录 / 令牌无效
- `400`: `MFA_ALREADY_ENABLED`（已启用，仅可关闭或重新生成恢复码）
- `401`: 密码错误（若传入 password）

---

### 2. 确认启用（enable）

**POST** `/api/v1/auth/2fa/enable`

**鉴权**: 已登录 JWT。

**请求体**:
```json
{ "code": "123456" }
```

**响应 200**:
```json
{
  "enabled": true,
  "enabledAt": "2026-09-11T10:00:00Z",
  "recoveryCodes": [
    "ABCDEFGH", "JKLMNPQR", "STUVWXYZ", "23456789", "BCDFGHJK",
    "MNPQRSTV", "WXYZ2345", "6789BCDF", "GHJKMNPQ", "RSTVWXYZ"
  ]
}
```

> 恢复码仅此一次以明文返回（10 个 8 位 Base32），落库只存「独立盐 + SHA-256」哈希。启用成功后账号进入已启用状态。

**错误响应**:
- `401`: `MFA_CODE_INVALID`（动态码错误，含剩余尝试次数）
- `400`: `MFA_NOT_ENABLED` 之前需先 setup（业务上指尚未生成待绑定密钥）
- `400`: `MFA_ALREADY_ENABLED`

---

### 3. 二次验证（verify）

**POST** `/api/v1/auth/2fa/verify`

**鉴权**: 无需 JWT；凭密码阶段签发的一次性 `mfaToken`。

**请求体**（动态码）:
```json
{ "mfaToken": "eyJ...", "code": "123456" }
```

**请求体**（恢复码）:
```json
{ "mfaToken": "eyJ...", "recoveryCode": "ABCDEFGH" }
```

**响应 200**:
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "expiresIn": 3600
}
```

**错误响应**:
- `401`: `MFA_TICKET_INVALID`（票据不存在/已消费/已过期，需重新走密码登录）
- `401`: `MFA_CODE_INVALID`（动态码错误，返回剩余尝试次数）
- `401`: `RECOVERY_CODE_INVALID`（恢复码无效或已使用）
- `429`: `MFA_LOCKED`（连续 5 次失败锁定，响应含剩余锁定秒数）

---

### 4. 查询状态（status）

**GET** `/api/v1/auth/2fa/status`

**鉴权**: 已登录 JWT。

**响应 200**:
```json
{
  "enabled": true,
  "enabledAt": "2026-09-11T10:00:00Z",
  "recoveryCodesRemaining": 8
}
```

---

### 5. 重新生成恢复码（regenerate）

**POST** `/api/v1/auth/2fa/recovery-codes/regenerate`

**鉴权**: 已登录 JWT，且已启用 2FA。

**请求体**:
```json
{ "password": "admin123" }
```

**响应 200**:
```json
{
  "recoveryCodes": [
    "ABCDEFGH", "JKLMNPQR", "STUVWXYZ", "23456789", "BCDFGHJK",
    "MNPQRSTV", "WXYZ2345", "6789BCDF", "GHJKMNPQ", "RSTVWXYZ"
  ]
}
```

> 旧恢复码全部作废（物理删除或标记失效）。

**错误响应**:
- `400`: `MFA_NOT_ENABLED`
- `401`: 密码错误

---

### 6. 关闭 2FA（disable）

**POST** `/api/v1/auth/2fa/disable`

**鉴权**: 已登录 JWT。

**请求体**:
```json
{ "password": "admin123", "code": "123456" }
```
或使用恢复码:
```json
{ "password": "admin123", "recoveryCode": "ABCDEFGH" }
```

**响应 200**:
```json
{ "disabled": true }
```

> 关闭后密钥与全部恢复码作废，账号回到未启用状态，并记录审计日志。

**错误响应**:
- `400`: `MFA_NOT_ENABLED`
- `401`: 密码错误
- `401`: `MFA_CODE_INVALID` / `RECOVERY_CODE_INVALID`

---

### 7. 管理员重置（admin reset）

**POST** `/api/v1/admin/users/{id}/2fa/reset`

**鉴权**: 管理员 JWT + 权限 `user:manage`。记录审计日志（操作人 = 管理员，目标 = 被重置用户）。

**请求体**: 无

**响应 200**:
```json
{ "reset": true }
```

> 关闭目标用户 2FA 并作废其密钥与恢复码；不得绕过密码阶段。目标用户下次登录按未启用 2FA 处理。

**错误响应**:
- `403`: 无 `user:manage` 权限
- `404`: 用户不存在

---

### 8. 登录改造（login）

**POST** `/api/v1/auth/login`

**鉴权**: 无（密码阶段）。

**请求体**:
```json
{ "username": "admin", "password": "admin123" }
```

**响应 — 已启用 2FA 的用户（HTTP 200）**:
```json
{ "mfaRequired": true, "mfaToken": "eyJ...", "expiresIn": 300 }
```

> `mfaToken` 为 Redis `mfa:ticket:{token}` 的票据（TTL 300 秒，一次性），**不含 `accessToken`**。

**响应 — 未启用 2FA 的用户**: 与现状**逐字节一致**（直接返回 `accessToken` 等），零回归。

---

## 错误码

| code | HTTP | 语义 |
|---|---|---|
| `MFA_REQUIRED` | 200 | 密码通过，需二次验证（登录响应 mfaRequired=true） |
| `MFA_TICKET_INVALID` | 401 | mfaToken 不存在 / 已消费 / 已过期 |
| `MFA_CODE_INVALID` | 401 | 动态码错误（响应含剩余尝试次数） |
| `MFA_LOCKED` | 429 | 连续 5 次失败锁定（响应含剩余锁定秒数） |
| `MFA_NOT_ENABLED` | 400 | 账号未启用 2FA，操作不适用 |
| `MFA_ALREADY_ENABLED` | 400 | 账号已启用 2FA，不允许重复启用 |
| `RECOVERY_CODE_INVALID` | 401 | 恢复码无效或已使用 |
| `MFA_SECRET_MISSING` | 500 | 服务端 MFA 密钥配置缺失（fail closed） |

---

## 安全约束

- TOTP 密钥以 AES-256-GCM 密文落库（`base64(iv):base64(ciphertext+tag)`），除 `setup` 一次性返回外，任何接口不回显明文。
- 恢复码仅存「独立盐 + SHA-256」哈希，明文仅在 `enable`/`regenerate` 一次性返回。
- `mfaToken` 一次性消费；同一用户同一时间步内动态码防重放（`mfa:used:{userId}:{timeStep}` TTL 90s）。
- 连续 5 次二次验证失败锁定 15 分钟（`mfa:fail:{userId}` TTL 900s）。
- Redis 不可用时，已启用 2FA 的登录**失败关闭**（拒绝登录），不得静默降级为单因素。

## 权限矩阵

| 端点 | 鉴权要求 |
|---|---|
| setup / enable / status / regenerate / disable | 已登录 JWT（本人账号） |
| verify | 一次性 mfaToken（无需 JWT） |
| admin reset | 管理员 JWT + `user:manage` 权限 |
| login | 无（密码阶段） |
