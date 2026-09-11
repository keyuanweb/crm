# 实施计划：双因素认证（TOTP 2FA）

## 概述

为账号增加基于 RFC 6238 TOTP 的第二因素认证与一次性恢复码逃生通道，覆盖 US1 登录二次验证、US2 绑定向导、US3 恢复码、US4 关闭/管理员重置；US5 强制策略本期不实现，仅在登录契约与数据模型中预留扩展点（`mfaSetupRequired` 字段位与策略判定挂点）。

## 技术上下文

- **TOTP（自研，不引第三方库）**：RFC 6238，HMAC-SHA1，6 位数字，30 秒步长，时间窗容差 ±1 步；使用 JDK `javax.crypto.Mac`（`HmacSHA1`）计算 HMAC，`commons-codec` 的 `Base32` 做密钥编解码，动态截断（dynamic truncation）由 `com.crm.common` 工具方法实现。
- **二维码**：`com.google.zxing:core` 生成 PNG 字节流后转 Base64 返回，对齐 017 图形验证码的返回风格，前端不新增任何依赖，仅用 `<img src="data:image/png;base64,...">` 展示。
- **密钥加密**：AES-256-GCM，JDK `javax.crypto.Cipher`（`AES/GCM/NoPadding`），密钥材料来自外部配置项（`crm.security.mfa.secret-key`，经环境变量 `MFA_SECRET_KEY` 注入，不硬编码），每条记录使用随机 12 字节 IV 并随密文一起落库。
- **Redis**：存短期票据（`mfa:ticket:{token}`）、失败计数（`mfa:fail:{userId}`）、防重放时间步（`mfa:used:{userId}:{timeStep}`），复用现有 RedisTemplate。
- **Flyway**：新增 `V78__two_factor_auth.sql`，并镜像到 `backend/src/test/resources/schema-h2.sql`（H2 兼容 DDL）。
- **springdoc-openapi**：新端点纳入既有 OpenAPI 契约，沿用统一响应包装与错误码结构。

## 章程检查

- **契约优先**：先写 `contracts/` 下的 2FA 端点 OpenAPI 契约与 DTO，再实现 controller/service，确保接口形状、错误码、字段名在实现前冻结。
- **分层架构**：严格 `controller → service → repository`，实体只放 `com.crm.entity`，DTO 只放 `com.crm.dto`，TOTP/加密/二维码等纯逻辑放入 `com.crm.common` 或 `com.crm.service`，`com.crm.security` 仅承担鉴权过滤与令牌，`com.crm.config` 承载加密密钥与 Redis 键前缀等配置注入。
- **测试优先**：TOTP 先写 RFC 6238 官方测试向量单测，加密先写密文不明文断言，票据/限流先写重放与锁定用例，再落实现。
- **安全默认（fail closed）**：Redis 不可用时拒绝已启用 2FA 用户的登录而非降级为单因素；密钥一律加密落库、任何 GET 不回显明文；失败路径默认拒绝。

## 关键设计决策

| 决策 | 理由 | 备选 |
| --- | --- | --- |
| 自研 TOTP 而非第三方库（如 `totp`/`otp-java`） | 依赖 JDK 自带 `Mac` 与 `commons-codec` 即可满足 RFC 6238，减少供应链面，便于按官方测试向量验证 | 引入 `dev.samstevens.totp` 等第三方库 |
| AES-256-GCM 加密密钥 | 提供机密性与完整性（认证加密），JDK 原生支持，随机 IV 避免同明文同密文 | AES-CBC、RSA 包裹密钥、明文存储（均不满足 FR-M11） |
| Redis 票据单次消费 | 原子 `GETDEL` 保证票据一次性、防重放、天然 300s TTL 过期 | 内存会话表（无法水平扩展）、JWT 短票据（不可撤销） |
| 防重放用 timeStep | 以 `{userId}:{timeStep}` 唯一键标记已消费步长，天然覆盖 ±1 容差窗口内的重放 | 存完整码值（浪费且需哈希比对） |
| 恢复码盐化哈希 | 每用户独立随机盐 + SHA-256 落库，只返回一次明文，泄露库表不泄露恢复码 | 明文落库、统一盐（均不安全） |
| 管理员重置需 `user:manage` 权限 | 重置是绕过用户二次验证的高危操作，必须走角色权限并记审计 | 任意管理员可重置、仅靠登录态 |

## 实现步骤

1. **迁移 V78__two_factor_auth.sql**：新增 `user` 表 2FA 扩展列（`two_factor_enabled`、`totp_secret_encrypted`、`two_factor_enabled_at`、`last_2fa_verified_at`，IV 并入密文列）与 `user_recovery_code` 表；同步镜像到 `backend/src/test/resources/schema-h2.sql`。
2. **ErrorCode 枚举新增 8 个码**：`MFA_REQUIRED`、`MFA_TICKET_INVALID`、`MFA_CODE_INVALID`、`MFA_LOCKED`、`MFA_NOT_ENABLED`、`MFA_ALREADY_ENABLED`、`RECOVERY_CODE_INVALID`、`MFA_SECRET_MISSING`，并补充中英文消息映射。
3. **实体 User 扩展字段 + `UserRecoveryCode` 实体 + repository**：`com.crm.entity.User` 增字段，新增 `com.crm.entity.UserRecoveryCode` 与 `com.crm.repository.UserRecoveryCodeRepository`（MyBatis-Plus）。
4. **TOTP 服务**：`com.crm.service.TotpService`（或 `com.crm.common.TotpUtil`）实现 RFC 6238 生成/校验（含 ±1 步容差），先落官方测试向量单测。
5. **密钥加密服务**：`com.crm.service.MfaSecretEncryptionService`（AES-256-GCM），密钥来自 `com.crm.config` 注入，提供 `encrypt/decrypt` 与「密文落库、无明文」测试。
6. **二维码服务**：`com.crm.service.QrCodeService`，用 zxing 生成 `otpauth://` 链接对应的 PNG，转 Base64 返回。
7. **MfaTicketService / 限流**：`com.crm.service.MfaTicketService` 封装 Redis——`mfa:ticket:{token}` TTL 300s 一次性（GETDEL）；`mfa:fail:{userId}` INCR TTL 900s 满 5 锁定；`mfa:used:{userId}:{timeStep}` TTL 90s 防重放。
8. **恢复码服务**：`com.crm.service.RecoveryCodeService`，生成 10×8 位 Base32，独立盐 + SHA-256 哈希落库，提供校验（一次性）与重新生成（旧码全失效）。
9. **AuthController 2FA 端点 + login 改造**：`com.crm.controller.AuthController` 新增 2FA 相关端点；login 命中已启用 2FA 用户时返回 `{mfaRequired, mfaToken, expiresIn: 300}` 且不含 `accessToken`。
10. **Admin 重置端点**：`com.crm.controller.AdminUserController`（或既有用户管理控制器）新增重置 2FA，`com.crm.security` 校验 `user:manage` 权限并写审计日志。
11. **前端**：登录二次验证视图、绑定向导（扫码/录入 → 输入动态码 → 保存恢复码）、账户安全页状态与剩余恢复码、管理员重置入口，并补齐中英文 i18n 资源。
12. **集成测试与回归**：`AuthMfaIT` 覆盖全流程，回归既有登录测试与前端 58 项测试，执行 `mvn test` 与 `pnpm run typecheck && pnpm run lint && pnpm run test`。

## 端点清单

- `POST /api/v1/auth/2fa/setup` — 生成密钥与二维码（一次性返回明文密钥/链接/图片），账号仍未启用
- `POST /api/v1/auth/2fa/enable` — 校验动态码，置为已启用并返回 10 个恢复码（仅本次明文）
- `POST /api/v1/auth/2fa/verify` — 凭 `mfaToken` + 动态码（或恢复码）完成二次验证，签发 `accessToken`/`refreshToken`
- `GET /api/v1/auth/2fa/status` — 返回当前用户 2FA 状态、绑定时间、剩余恢复码数量
- `POST /api/v1/auth/2fa/recovery-codes/regenerate` — 重新生成恢复码，旧码全部失效
- `POST /api/v1/auth/2fa/disable` — 密码 + 动态码（或恢复码）关闭 2FA，作废密钥与恢复码并审计
- `POST /api/v1/admin/users/{id}/2fa/reset` — 管理员重置 2FA（需 `user:manage`），关闭并要求重新绑定并审计

（`login` 端点属既有端点改造：命中 2FA 用户时返回 `{mfaRequired, mfaToken, expiresIn: 300}` 而非 `accessToken`。）

## 测试策略

- **RFC 6238 官方测试向量**：用 `HMAC-SHA1` 已知密钥与时间戳向量断言生成码值，覆盖 30 秒步长与 ±1 步容差。
- **重放/恢复码重用 100% 拒绝断言**：同一时间步重复提交、同一恢复码二次提交，自动化测试断言全部返回拒绝（对应 SC-M05）。
- **非 2FA 登录逐字节兼容**：未启用 2FA 用户的登录响应与既有契约逐字段一致，58 项前端测试保持绿（SC-M08、FR-M14）。
- **Redis 不可用 fail closed 测试**：mock/停用 Redis 后断言已启用 2FA 用户登录被拒绝且不签发令牌，不降级为单因素。
- **密钥密文落库断言**：单测断言数据库持久化内容为密文，不含明文 Base32 密钥（SC-M06）。

## 风险与缓解

- **Redis 依赖**：票据、限流、防重放均依赖 Redis；Redis 故障时已启用 2FA 用户无法登录。缓解：明确失败关闭策略（不降级），纳入运维监控告警，与 017 图形验证码共享既有 Redis 高可用能力。
- **时间漂移**：认证器与服务端时钟偏差导致校验失败。缓解：±1 步容差（±30s）默认开启，服务端以系统时钟为准，必要时可评估更宽窗口但限制重放窗口代价。
- **密钥管理**：加密密钥泄露将导致全部 TOTP 密钥失守。缓解：密钥来自环境变量/配置中心，不硬编码、不入库、不入日志，支持轮换（解密→重加密）。
- **恢复码丢失**：恢复码用尽且设备丢失将锁死账号。缓解：管理员重置作为兜底，账户安全页在剩余 ≤ 2 时提示重新生成。

## 非目标

- **US5 强制策略**：本期不实现按角色强制绑定与强制引导登录，仅预留 `mfaSetupRequired` 扩展点与策略判定挂点。
- **SMS/邮件找回**：不做短信或邮件找回恢复码/重置 2FA 通道，恢复仅靠恢复码与管理员重置。
- **开放平台鉴权**：不改变开放平台 API Key 与 Webhook 的既有鉴权方式，2FA 仅作用于主站登录。
