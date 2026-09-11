# 验证指南：双因素认证（TOTP 2FA）

本文档用于快速验证 082-two-factor-auth 双因素认证模块。功能以 TOTP（RFC 6238，HMAC-SHA1，6 位，30 秒步长）为核心，配合一次性恢复码与管理员重置，覆盖企业合规与账号盗用防护需求。验收标准以 `spec.md` 的 SC-M01~SC-M08 为准。

## 环境依赖

- **Redis 7**：必须可用（监听 6379）。`mfaToken` 短期票据、二次验证失败计数与锁定、防重放时间步均依赖 Redis；Redis 不可用时，已启用 2FA 的登录会被拒绝（失败关闭），不得静默降级为单因素。
- **MySQL**：`crm_db` 必须可连接（监听 3306），用于用户 2FA 字段与恢复码哈希的持久化。
- **MFA_SECRET_KEY 环境变量**：TOTP 密钥加密所需的 AES-256-GCM 密钥材料，来自外部配置项，**不得硬编码**。建议为 32 字节随机值的 Base64 编码，例如：

  ```powershell
  # 生成一个 32 字节密钥的 Base64 编码（仅示例，勿在生产使用此值）
  $bytes = New-Object byte[] 32
  [System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
  $env:MFA_SECRET_KEY = [Convert]::ToBase64String($bytes)
  ```

  > 若未配置该密钥，后端应报 `MFA_SECRET_MISSING` 错误（见「常见问题」）。

## 配置

`application.yml` 片段示例（后端 `backend/src/main/resources/application.yml`，按实际配置项名称为准）：

```yaml
crm:
  security:
    mfa:
      # 优先读取环境变量 MFA_SECRET_KEY，缺失时报 MFA_SECRET_MISSING
      secret-key: ${MFA_SECRET_KEY}
      # mfaToken 有效期（秒），与 FR-M02 的 TTL 300 秒一致
      token-ttl: 300
      # 时间窗容差（±1 步，即 ±30 秒），与 FR-M01 一致
      time-step: 30
      time-step-tolerance: 1
      # 连续失败锁定：5 次失败锁 15 分钟（FR-M07）
      max-attempts: 5
      lock-duration: 900
      # 恢复码数量（FR-M05）
      recovery-code-count: 10
```

若仅通过环境变量注入，可等价为：

```powershell
$env:MFA_SECRET_KEY = "<32 字节的 Base64 值>"
```

## 迁移与启动

1. **后端**（Flyway 会自动执行 `V78` 迁移，创建 2FA 相关字段与恢复码表）：

   ```powershell
   cd E:\code\crm\backend
   mvn spring-boot:run
   ```

   服务启动在 8081 端口。`V78` 迁移脚本已同步 H2（测试环境）与 MySQL，无需手动执行 SQL。

2. **前端**：

   ```powershell
   cd E:\code\crm\frontend
   pnpm run dev
   ```

   前端启动在 5173 端口。

3. **验证测试**（可选，对应 SC-M07）：

   ```powershell
   cd E:\code\crm\backend
   mvn test
   ```

   ```powershell
   cd E:\code\crm\frontend
   pnpm run typecheck; pnpm run lint; pnpm run test
   ```

## 冒烟验收流程

以下命令使用 PowerShell `Invoke-RestMethod`。变量 `$base` 指向后端地址；示例响应为预期 JSON 结构，字段名与内容以实际实现为准。

```powershell
$base = "http://localhost:8081"
```

### a) 登录获取 JWT（未启用 2FA 的账号）

先用一个**未启用 2FA** 的账号登录，行为必须与现状一致（直接签发令牌，零回归）。

```powershell
$body = @{ username = "alice"; password = "Passw0rd!" } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri "$base/api/v1/auth/login" -ContentType "application/json" -Body $body
$login.accessToken   # 未启用 2FA 时直接返回 accessToken / refreshToken
```

预期：`$login.accessToken` 非空，直接进入系统，无任何 2FA 额外流程。

### b) POST /api/v1/auth/2fa/setup —— 生成绑定密钥

携带上述 JWT，发起绑定，获取 secret / otpauthUrl / qrCodeDataUrl（此时账号仍为**未启用**状态）。

```powershell
$h = @{ Authorization = "Bearer $($login.accessToken)" }
$setup = Invoke-RestMethod -Method Post -Uri "$base/api/v1/auth/2fa/setup" -Headers $h
$setup.secret          # Base32 密钥
$setup.otpauthUrl      # otpauth://totp/... 链接
$setup.qrCodeDataUrl   # 二维码 PNG 的 data URL（Base64）
```

预期：返回 Base32 密钥、`otpauth://` 链接与二维码图片，账号保持未启用状态。

### c) 用 secret 生成当前 TOTP

可将 `$setup.otpauthUrl` 或 `$setup.secret` 录入任意 TOTP App（Google/Microsoft Authenticator、1Password 等）。命令行自测可用以下 RFC 6238（HMAC-SHA1，6 位，30 秒步长）PowerShell 实现：

```powershell
function Get-Totp([string]$secret) {
  $b32 = $secret -replace '[^A-Za-z2-7]', '' -replace '=',''
  $bits = -join ($b32.ToUpper().ToCharArray() | ForEach-Object {
    [Convert]::ToString("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".IndexOf($_), 2).PadLeft(5, '0')
  })
  $key = for ($i = 0; $i -lt $bits.Length; $i += 8) {
    [Convert]::ToByte($bits.Substring($i, [Math]::Min(8, $bits.Length - $i)).PadRight(8, '0'), 2)
  }
  $counter = [BitConverter]::GetBytes([uint64]([int64][Math]::Floor(([DateTimeOffset]::UtcNow.ToUnixTimeSeconds()) / 30)))
  [Array]::Reverse($counter)
  $hmac = New-Object System.Security.Cryptography.HMACSHA1
  $hmac.Key = $key
  $hash = $hmac.ComputeHash($counter)
  $offset = $hash[$hash.Length - 1] -band 0x0F
  $code = (($hash[$offset] -band 0x7F) -shl 24) -bor ($hash[$offset+1] -shl 16) -bor ($hash[$offset+2] -shl 8) -bor $hash[$offset+3]
  ($code % 1000000).ToString("D6")
}
$code = Get-Totp $setup.secret
$code
```

### d) POST /api/v1/auth/2fa/enable —— 校验动态码并启用

输入认证器当前显示的 6 位码完成绑定，成功后返回 10 个一次性恢复码。

```powershell
$enableBody = @{ code = $code } | ConvertTo-Json
$enable = Invoke-RestMethod -Method Post -Uri "$base/api/v1/auth/2fa/enable" -Headers $h -ContentType "application/json" -Body $enableBody
$enable.recoveryCodes   # 10 个恢复码，仅本次以明文返回一次
```

预期：2FA 状态置为「已启用」，返回 10 个恢复码。**请立即保存**这 10 个恢复码，后续将无法再次取回明文。

### e) GET /api/v1/auth/2fa/status —— 查看状态

```powershell
$status = Invoke-RestMethod -Method Get -Uri "$base/api/v1/auth/2fa/status" -Headers $h
$status
```

预期：返回 `enabled: true`、绑定时间与剩余恢复码数量（FR-M13），不返回明文密钥。

### f) 重新登录：返回 mfaRequired

退出后用**已启用 2FA** 的账号重新登录，密码通过后不再直接签发令牌。

```powershell
$login2 = Invoke-RestMethod -Method Post -Uri "$base/api/v1/auth/login" -ContentType "application/json" -Body $body
$login2.mfaRequired   # true
$login2.mfaToken      # 一次性短期票据（TTL 300 秒）
$login2.expiresIn     # 300
```

预期：响应**不包含** `accessToken`，返回 `{ "mfaRequired": true, "mfaToken": "...", "expiresIn": 300 }`。

### g) POST /api/v1/auth/2fa/verify —— 动态码二次验证

```powershell
$code2 = Get-Totp $setup.secret
$verifyBody = @{ mfaToken = $login2.mfaToken; code = $code2 } | ConvertTo-Json
$verify = Invoke-RestMethod -Method Post -Uri "$base/api/v1/auth/2fa/verify" -ContentType "application/json" -Body $verifyBody
$verify.accessToken    # 校验通过后签发访问令牌
$verify.refreshToken
```

预期：返回 `accessToken` 与 `refreshToken`，正常进入系统。

### h) 用恢复码验证

重新登录获取新的 `mfaToken` 后，改用恢复码完成二次验证。

```powershell
$login3 = Invoke-RestMethod -Method Post -Uri "$base/api/v1/auth/login" -ContentType "application/json" -Body $body
$rcBody = @{ mfaToken = $login3.mfaToken; recoveryCode = "<步骤 d 保存的某个未使用恢复码>" } | ConvertTo-Json
$rc = Invoke-RestMethod -Method Post -Uri "$base/api/v1/auth/2fa/verify" -ContentType "application/json" -Body $rcBody
$rc.accessToken
```

预期：校验通过并签发令牌；该恢复码被标记为已使用，**再次提交同一恢复码会被拒绝**。

### i) POST /api/v1/auth/2fa/disable —— 关闭 2FA

用「密码 + 当前动态码」关闭 2FA（也可用恢复码）。

```powershell
$code3 = Get-Totp $setup.secret
$disableBody = @{ password = "Passw0rd!"; code = $code3 } | ConvertTo-Json
$disable = Invoke-RestMethod -Method Post -Uri "$base/api/v1/auth/2fa/disable" -Headers $h -ContentType "application/json" -Body $disableBody
$disable
```

预期：2FA 关闭，密钥与恢复码作废并记录审计日志；下次登录直接签发令牌（FR-M09）。

### j) 管理员重置：POST /api/v1/admin/users/{id}/2fa/reset

管理员（需 `user:manage` 权限）为遗忘设备的用户重置 2FA。

```powershell
$adminBody = @{ username = "admin"; password = "AdminPassw0rd!" } | ConvertTo-Json
$admin = Invoke-RestMethod -Method Post -Uri "$base/api/v1/auth/login" -ContentType "application/json" -Body $adminBody
$adminH = @{ Authorization = "Bearer $($admin.accessToken)" }
$userId = "<目标用户 ID>"
$reset = Invoke-RestMethod -Method Post -Uri "$base/api/v1/admin/users/$userId/2fa/reset" -Headers $adminH
$reset
```

预期：该用户 2FA 被关闭、恢复码作废，并记录审计日志（操作人 = 管理员，FR-M10）；被重置用户重新登录时走单因素流程并被要求重新绑定。

## 验收清单

- [ ] **SC-M01**：已启用 2FA 的用户完成「密码 → 动态码」全流程 ≤ 30 秒。
- [ ] **SC-M02**：绑定流程 ≤ 3 步（扫码/录入密钥 → 输入动态码 → 保存恢复码），首次成功率 ≥ 90%（含 ±1 步时间容差）。
- [ ] **SC-M03**：未启用 2FA 用户的登录接口响应时间无退化（P95 增幅 ≤ 50 ms，零回归）。
- [ ] **SC-M04**：连续错误 5 次后锁定 15 分钟生效，第 6 次即使动态码正确也被拒绝，锁定期满后自动恢复。
- [ ] **SC-M05**：动态码重放与恢复码重用在自动化测试中 100% 被拒绝。
- [ ] **SC-M06**：密钥以 AES-256-GCM 密文落库，数据库中不存在明文 TOTP 密钥（人工抽查 + 单测断言）。
- [ ] **SC-M07**：后端 `mvn test`（含 MfaService 单测与 AuthMfaIT 集成测试）通过；前端 `pnpm run typecheck && pnpm run lint && pnpm run test` 通过。
- [ ] **SC-M08**：未启用 2FA 的既有前端 58 项测试全部保持通过（零回归）。

## 常见问题

- **时间漂移导致动态码校验失败**：认证器与服务端时间偏差允许 ±1 个时间步（±30 秒窗口）。若持续失败，请校准设备时间（系统以服务器时间为准，不做 NTP 校时）。
- **启动报 `MFA_SECRET_MISSING`**：未配置 `MFA_SECRET_KEY` 环境变量（或 `crm.security.mfa.secret-key`）。请按「环境依赖」章节生成 32 字节 Base64 密钥并注入后重启后端。
- **Redis 不可用**：已启用 2FA 的登录会失败关闭（拒绝登录），不会静默降级为单因素。请先恢复 Redis（6379）再重试。
- **恢复码丢失 / 全部用完**：系统不提供短信/邮件找回。请联系管理员执行 `POST /api/v1/admin/users/{id}/2fa/reset` 重置 2FA 并重新绑定。
- **重复提交同一动态码被拒绝**：同一时间步内已使用过的动态码受防重放保护，属预期行为，请等待下一个 30 秒时间步。
- **`mfaToken` 过期或重复使用返回 401**：`mfaToken` 有效期 5 分钟且一次性消费，过期或重复使用需重新走密码登录流程。
