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

---

## ⚠️ 订正（2026-09-16，**事后按现状订正**）—— 本文件受影响最大

**性质说明**：本契约写于 2026-09-11，此后 13 批落地。以下按**今天的实测现状**逐条订正，
**属"事后按现状订正"（照 090/094/095 的先例）**。原文一律逐字保留在上方、不删改。
**下面每一条都改变了客户端要写的代码**，不是措辞问题。

### 一、错误信封：`{code, message}` → **`ApiResponse` 的三层包裹**

**原文（保留）**：基础信息「**错误信封**：统一 `{ "code": "<ErrorCode>", "message": "<本地化描述>" }`」。

**实测**：本仓统一信封是
```json
{ "success": false, "data": null, "error": { "code": "…", "message": "…", "fieldErrors": null } }
```
`code` / `message` 嵌在 **`error`** 对象里，**不在顶层**。`GlobalExceptionHandler.handleBusiness`
用 `code.getStatus()` 定 HTTP 码、`code.getCode()` 定 `error.code`、`ex.getMessage()` 定 `error.message`。
⇒ 前端取错误码要写 `err.response.data.error.code`，**不是** `err.response.data.code`。

### 二、`verify` 返回 **完整 `AuthResponse`**，不是两个令牌 + `expiresIn`

**原文（保留）**：§3「响应 200：`{accessToken, refreshToken, expiresIn: 3600}`」。

**订正**：改为与 `login` / `refresh` **同一个** `AuthResponse`：
```json
{ "accessToken": "…", "refreshToken": "…", "user": { "id": 1, "username": "admin", "menus": [...], "permissions": [...] } }
```
`expiresIn` **从 `verify` 的响应里消失**（见 §三）。理由：前端渲染外壳与菜单要的是 `user.menus` / `user.permissions`，
只回令牌会迫使前端**多打一次** `GET /auth/me` —— 而这次额外往返恰好落在 SC-M01 的 30 秒计时区间里。

### 三、`expiresIn` 的语义**收敛为唯一一个**（消灭同名不同义）

**原文（保留）**：§3 `expiresIn: 3600`（令牌有效期）与 §8 `expiresIn: 300`（票据 TTL）—— 同名、**不同义**。

**订正**：`expiresIn` **只在 §8 的 `mfaRequired` 分支出现**，值 **300**，语义**唯一** = `mfaToken` 的 TTL 秒数。
`verify` 与 `login` 的正常分支都**没有** `expiresIn`。
⇒ 一个字段只有一个意思；前端不必按端点猜它指的是令牌还是票据。

### 四、错误码表：`MFA_REQUIRED` **删除**，新增 `MFA_STORE_UNAVAILABLE`

**原文（保留）**：错误码表首行「`MFA_REQUIRED` | **200** | 密码通过，需二次验证（登录响应 mfaRequired=true）」。

**订正**：**删掉这一行**。「需要二次验证」**不是错误**，它由 §8 响应体里的 `mfaRequired: true` 表达。
把它列进 `ErrorCode` 会是**永不可达的死枚举项**（详见 `plan.md` 订正块 §二）。
另**新增** `MFA_STORE_UNAVAILABLE`(**503**)：Redis 故障时的 fail-closed 出口（见 §七）。

**真实错误码表（8 个，全部可达）**：

| code | HTTP | 语义 |
|---|---|---|
| `MFA_TICKET_INVALID` | 401 | mfaToken 不存在 / 已消费 / 已过期 |
| `MFA_CODE_INVALID` | 401 | 动态码错误（`error.message` 含剩余尝试次数） |
| `RECOVERY_CODE_INVALID` | 401 | 恢复码无效或已使用 |
| `MFA_LOCKED` | **429** | 连续 5 次失败锁定（`error.message` 含剩余锁定秒数）——**全仓首个 429** |
| `MFA_NOT_ENABLED` | 400 | 账号未启用 2FA，操作不适用 |
| `MFA_ALREADY_ENABLED` | 400 | 账号已启用 2FA，不允许重复启用 |
| `MFA_SECRET_MISSING` | 500 | 服务端 MFA 密钥未配置（fail closed） |
| `MFA_STORE_UNAVAILABLE` | **503** | 状态存储（Redis）不可用（fail closed，**不得降级为单因素**） |

### 五、「剩余尝试次数」/「剩余锁定秒数」经 **`error.message`** 承载，**不新增字段**

**原文（保留）**：§3「`401`: `MFA_CODE_INVALID`（动态码错误，响应含剩余尝试次数）」；
错误码表「`MFA_LOCKED` | 429 | …（响应含剩余锁定秒数）」。

**订正**：原文说"响应含"但**没规定位置**。本仓的 `GlobalExceptionHandler` 只渲染
`{code, message, fieldErrors}` 三个字段，**不给业务码附加结构化载荷**。
⇒ 两个数字都拼进 **`error.message`**（中文文本，如「动态码错误，剩余尝试次数 3」）。
**不新增响应字段**——那要么改 `ErrorResponse` 的形状（影响全仓每一个错误响应），
要么为这两个数单开一个旁路结构，代价都远超收益。
**代价如实记**：前端若想结构化地用这两个数字，**做不到**，只能整句展示。这是**有意的取舍**，不是遗漏。

### 六、管理员重置路径：`/api/v1/admin/users/{id}/2fa/reset` → **`/api/v1/users/{id}/2fa/reset`**

**原文（保留）**：基础信息「管理端重置为 `/api/v1/admin/users/{id}/2fa`」、§7 标题、§7 末「常见问题」同。

**实测**：后端**不存在 `/api/v1/admin/**` 前缀**。用户管理端点在 `UserController`，挂 **`/api/v1/users`**。
⇒ **`POST /api/v1/users/{id}/2fa/reset`**，与 `PUT /{id}/password` 等管理动作同处一个控制器、同一个
`@RequirePermission("user:manage")`。§7 的其余内容（请求体无、响应 `{"reset": true}`、403/404、审计）**不变**。

### 七、Redis 键族 `mfa:*` → **`auth:2fa-*`**

**原文（保留）**：§8 注「`mfaToken` 为 Redis `mfa:ticket:{token}` 的票据」；安全约束末三条。

**订正**：**`auth:2fa-ticket:` / `auth:2fa-fail:` / `auth:2fa-used:`**，与仓内既有
`auth:refresh:` / `auth:fail:` / `auth:captcha:` 同族。**TTL 与语义一字不改**（300s / 900s / 90s）。

### 八、`mfaToken` **不是 JWT**（原文样例形如 `"eyJ..."`）

**原文（保留）**：§3 请求体「`{ "mfaToken": "eyJ...", "code": "123456" }`」；§8「`{ "mfaRequired": true, "mfaToken": "eyJ...", "expiresIn": 300 }`」。

**订正**：`mfaToken` 是 **`SecureRandom` 32 字节 → Base64URL 的不透明随机串**（形如 `xQ7…` 而**不是** `eyJ…`）。
`eyJ` 是 JWT 的 base64url 头 `{"` 的特征前缀，客户端**不得**据此解析它。
票据的权威在服务端 Redis；做成 JWT 会引入第二个真源（见 `plan.md` 订正块 §三）。
⇒ **客户端契约上唯一要改的是「别解析它」**——原样存、原样回传即可。

### 九、`enabledAt` **不带 `Z`**，是**无时区**的 `LocalDateTime`

**原文（保留）**：§2 / §4 的 `"enabledAt": "2026-09-11T10:00:00Z"`。

**订正**：形如 **`"2026-09-16T10:23:45"`**，**无 `Z`、无偏移量**。
仓内所有 DTO 的时间字段都是 `LocalDateTime`（`application.yml:20-22` 另配
`spring.jackson.time-zone: Asia/Shanghai`），**不为这一个字段单独引入 `OffsetDateTime`**——
那会让同一个响应里出现两种时间形态（有的带时区有的不带），前端得写两套解析。
⇒ `Z` 是**样例笔误**，不是格式规定。前端按"本地时间字符串"处理。

### 十、`setup` 的请求体是**可选**的，且无 body 时**不得** 400

**原文（保留）**：§1「**请求体**（可选，用于密码二次确认）」。

**订正**：原文写"可选"，但**实现必须显式支持"整个 body 缺席"** ——
须用 `@RequestBody(required = false)`。否则 `quickstart.md` §b 那条**不带 body** 的
`Invoke-RestMethod` 会得到一个**费解的 400**（Spring 在参数解析阶段就拒绝了，与业务无关）。
本批按"无 body 合法"实现。

### 十一、恢复码是 **8 位无歧义字符**，不是 Base32

**原文（保留）**：§2 注「恢复码仅此一次以明文返回（10 个 8 位 Base32）」；
样例数组里的 `"23456789"`、`"6789BCDF"`。

**订正**：**样例自己就不满足 Base32** —— 标准 Base32（RFC 4648）字母表是 `A-Z2-7`，**不含 `8`/`9`**，
而样例里有 `8` 和 `9`。真实字母表取仓库既有的 **`CaptchaService.CHARS`**
= `ABCDEFGHJKMNPQRSTUVWXYZ23456789`（31 符号，剔除易混的 `I`/`L`/`O`）。
⇒ **样例数组本身仍然有效**（它用的正是这个字母表），只需把"Base32"这个**词**改掉。
§5 的 `regenerate`、`spec.md` 的假设第 4 条同此订正。

### 十二、§7 权限矩阵的「admin reset」一行随 §六 移动

§7 的路径改了（§六），**权限要求不变**（仍为 `user:manage`）。
下方权限矩阵表里的「admin reset」一行按新路径读。**`user:manage` 是既有权限码，本批不新增任何权限码** ——
`RoleConstants` 里它已存在，且 `PermissionAspect` 对 `ADMIN` 角色直通，
另有 `RequirePermissionCatalogTest`（注解 ⊆ 字典）与 `PermissionMatrixIT`（授予 ⊆ 字典）双向门禁兜底。
