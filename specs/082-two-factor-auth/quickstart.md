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

---

## ⚠️ 订正（2026-09-16，**事后按现状订正**）

**性质说明**：本文件写于 2026-09-12，此后 13 批落地。以下按**今天的实测现状**订正，
**属"事后按现状订正"（照 090/094/095 的先例）**。原文一律逐字保留在上方、不删改。
**§a–§j 的流程本身全部成立**（本批就是按它实现的），要改的是**配置片段、迁移号、两条路径与一个计数**。

### 一、「启动报 `MFA_SECRET_MISSING`」是**错的** —— 缺密钥**不会**让启动失败

**原文（保留）**：`## 环境依赖`「若未配置该密钥，**后端应报 `MFA_SECRET_MISSING` 错误**」；
`## 常见问题`「**启动报 `MFA_SECRET_MISSING`**：未配置 `MFA_SECRET_KEY`…请…注入后重启后端」；
`## 配置` 的 yaml 片段 `secret-key: ${MFA_SECRET_KEY}`（**无默认值**）。

**订正（三条）**：

1. **yaml 片段要写成 `secret-key: ${MFA_SECRET_KEY:}`** —— 原文**没有默认值**，
   而 `application.yml` 里**引用一个不存在的占位符会让 Spring 启动失败**，与"启动只告警"直接冲突。
   加空的默认值 `${MFA_SECRET_KEY:}` 才是"读不到就是空"。
2. **缺密钥 ⇒ 启动成功 + 告警日志**，只在**调用 2FA 功能时**抛 `MFA_SECRET_MISSING`。
3. **"常见问题"的那一条要改写为**：「**调用 2FA 接口报 `MFA_SECRET_MISSING`**：未配置…」
   —— 症状出现在**接口调用**，不是**启动**。

**唯一会阻止启动的情形**是另一个：密钥**存在但 Base64 解码后不是 32 字节**（误配必须响）。
**理由是照 `SecurityDefaultsGuard` 抄会炸掉整个测试套件**（`@ActiveProfiles("test")` 不含 `"dev"`，
会被判为非 dev 而硬失败），且会拒绝启动任何**不用 2FA 的部署** —— 与"2FA 是可选功能"自相矛盾。
详见 `plan.md` 订正块 §八。

> ⚠️ **补充（2026-09-16，真机验证）**：上面第 2、3 条与"唯一会阻止启动的情形"**已于当日手工冒烟实测确认**
> （隔离实例：无密钥 18082 / `crm_mfa_nokey`；畸形密钥 18083）。读数：无密钥 ⇒ **启动成功**
> （`Started CrmApplication in 11.087 seconds`）只打一条 WARN，普通登录响应与有密钥实例**逐字段一致**，
> 而 `POST /auth/2fa/setup` ⇒ **500 `MFA_SECRET_MISSING`**（fail closed），`status` 只读路径仍 200；
> 密钥存在但 Base64 解码后为 4 字节 ⇒ **启动即失败、退出码 1**。
> 该条订正**已被真机证实**。证据见 `falsification-evidence.md` §J.2。

### 二、迁移号 `V78` → **`V89`**（两处）

**原文（保留）**：`## 迁移与启动` 第 1 条「Flyway 会自动执行 **`V78`** 迁移」与末句「**`V78`** 迁移脚本已同步 H2…」。

**订正**：均为 **`V89__two_factor_auth.sql`**。`V78` 已被 `V78__sla_escalation.sql`（1.3 批）占用，
当前最高 `V88`。详见 `plan.md` 订正块 §一。

### 三、管理员重置路径：`/api/v1/admin/users/{id}/2fa/reset` → **`/api/v1/users/{id}/2fa/reset`**

**原文（保留）**：§j 标题与 `## 常见问题` 末条的路径。

**订正**：本批重置端点挂在 **`/api/v1/users/{id}/2fa/reset`**（后端**不存在** `/api/v1/admin/**` 前缀）。
§j 的 `Invoke-RestMethod` 那一行要相应改：
```powershell
$reset = Invoke-RestMethod -Method Post -Uri "$base/api/v1/users/$userId/2fa/reset" -Headers $adminH
```
其余（`user:manage` 权限、审计、被重置用户回到单因素）**不变**。

### 四、§j 的示例口令与仓库种子不符（照实改）

**原文（保留）**：§j `$adminBody = @{ username = "admin"; password = "AdminPassw0rd!" }`。

**订正**：仓库**测试/开发种子**的管理员口令是 **`admin123`**（`AbstractIntegrationTest.loginAndGetToken()` 用的就是它）。
`AdminPassw0rd!` 在本仓**不成立**，照抄会拿到 401 而误以为是 2FA 的问题。
（§a 的 `alice` / `Passw0rd!` 同理是**示例账号**，本仓种子库里没有 `alice`——
跑冒烟前请把这两处的账号口令换成你**实际环境**里存在的账号。）

### 五、§d/§e 的 `$h` 用的是**步骤 a 的令牌**，这一步在真实冒烟里要留意

**原文（保留）**：§b 起沿用 `$h = @{ Authorization = "Bearer $($login.accessToken)" }`。

**订正（不是错，是提醒）**：`$h` 来自**步骤 a 的未启用账号**，而 §b–§e 又要拿它去**给同一个账号**绑定 2FA。
这在**同一个账号**上是成立的（§a 登录时它还没启用 2FA，拿到的是真令牌；绑定过程中它仍是未启用态），
但**先决条件是这个账号就是 §a 那个账号**。若照抄时把 §a 换成了别的账号，§b 会用**另一个人的令牌**去绑 2FA
而拿到 403/401 —— 那不是缺陷。**跑之前先确认 §a 与 §b 是同一个账号。**

### 六、计数：既有前端测试 `58` → **86 文件 / 429 用例**

**原文（保留）**：`## 验收清单` 的 `SC-M08`「未启用 2FA 的既有前端 **58** 项测试全部保持通过」。

**订正**：实测 **86 文件 / 429 用例**（`git ls-tree -r --name-only HEAD | grep -Ec 'frontend/src/.*\.test\.tsx?$'`）。
**判据（零回归）不变**。

### 七、验收清单里**只能靠眼睛**的两条，本批**未验证**（如实记）

- `SC-M01`「全流程 ≤ 30 秒」——需要真机 + 计时，本批**未测**；
- `SC-M02`「首次成功率 ≥ 90%」——需要多人多次真实绑定，本批**未测**。

这两条**不是自动化能替代的**，也**不应被读作已通过**。
本批的自动化覆盖见 `plan.md` 的「定向破坏留痕」表；`SC-M04`/`SC-M05`/`SC-M06`/`SC-M08` 有自动化判据，
`SC-M03`（P95 增幅 ≤ 50 ms）只有"未启用用户**响应体**逐字节不变"这个**结构性**判据，
**延迟本身未测**。

> ⚠️ **补充（2026-09-16，手工冒烟后）**：`SC-M04`（5 次锁定 / 第 6 次即使码正确也拒）与
> `SC-M05`（动态码重放、恢复码重用）**当日已在真机上取到读数**；`SC-M01` / `SC-M02` 仍未测，
> **`SC-M03` 的延迟仍未测**（只验了结构侧）。另：「锁定期满 15 分钟自动恢复」本次**没等**
> （用"删掉 `auth:2fa-fail:<id>` 键即恢复"代替，证明的是"锁无第二真源"，**不是**"到期自恢复"，
> 后者由 `AuthMfaIT` 的冻结时钟覆盖）。详见 `falsification-evidence.md` §J.1 / §J.5。

### 八、§c 的 PowerShell 算码片段**本身是坏的**（两个独立缺陷，照抄必失败）

**原文（保留）**：§c「用 secret 生成当前 TOTP」整段（`$b32` 查表 + `Get-Totp`）。

**症状**：照抄原文算出的码，拿去 §d `enable` 会得到 **401 `MFA_CODE_INVALID`** ——
看起来像"实现有问题"，实际是**片段算错了**。本次冒烟实测复现了这一条。

**两个缺陷各自独立，改掉一个不够**：

1. **Base32 字母表错位**。原文用
   ```powershell
   $b32 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
   ```
   而**标准 Base32 字母表是 `ABCDEFGHIJKLMNOPQRSTUVWXYZ234567`**。前者把每个**字母**的值
   **整体 +10**（`M` 应为 12，它算成 22）⇒ 密钥解出来就是错的，后面再对也无用。
   原文里 `$b32 = $secret -replace '[^A-Za-z2-7]',''` 那句又是有意收窄到标准字母表的，
   两处**自相矛盾**。
2. **PowerShell 的 `-shl` 结果类型跟随左操作数**。`$hash` 的元素是 `[byte]`，于是
   `$hash[$offset+1] -shl 16` 在 **byte 里溢出 = 0**（`[byte]57 -shl 16` → `0`，
   而 `[int]57 -shl 16` → `3735552`）。原文的动态截断四项里**只有首末两项活下来**。
   实测 `T=59` 得 1090519274 / `519274`，正确值是 1094287082 / `94287082`。
   > （`-band 0x7F` 那一步侥幸没事：与 Int32 字面量做 `-band` 会把类型提升成 Int32。）

**修法**（两处都改）：

```powershell
# ① 标准 Base32 字母表（A–Z 在前、2–7 在后）
$script:B32 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'
# ② 动态截断前先把每个字节显式转成 [int]
$b0 = [int]$hash[$offset] -band 0x7F
$b1 = [int]$hash[$offset + 1]
$b2 = [int]$hash[$offset + 2]
$b3 = [int]$hash[$offset + 3]
$code = ($b0 -shl 24) -bor ($b1 -shl 16) -bor ($b2 -shl 8) -bor $b3
```

**改完必须先自证再用**：拿 RFC 6238 附录 B 的 SHA1 六个向量核对（传的是
`floor(T/30)`，**不是** `T` 本身 —— 传错时间步会六项全不匹配）：

```
counter=1          → 287082     (T=59)
counter=37037036   → 081804     (T=1111111109)
counter=37037037   → 050471     (T=1111111111)
counter=41152263   → 005924     (T=1234567890)
counter=66666666   → 279037     (T=2000000000)
counter=666666666  → 353130     (T=20000000000)
```

六项全中才允许用它算码。本次另用 **Python 3.12 的 `hmac`** 独立算了一遍同样六个向量做交叉核对
（HMAC 十六进制逐字节相同）⇒ **错的是这段 PowerShell，不是 RFC，也不是本批的 Java 实现**。

⚠️ **对本批结论的影响：无。** TOTP 的判据在 Java 侧（`TotpGenerator` + 官方向量单测）。
本节只影响"怎么在命令行自己算一个码"这个操作指引。逐条读数见 `falsification-evidence.md` §J.3。
