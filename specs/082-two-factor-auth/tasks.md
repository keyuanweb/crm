# 实施任务：双因素认证（TOTP 2FA）

## 阶段 1：数据层

- [ ] T001 Flyway 迁移 `V78__two_factor_auth.sql`：`user` 表新增 4 列（是否启用 2FA、加密后的 TOTP 密钥、绑定时间、最近一次二次验证时间），新建 `user_recovery_code` 表（所属用户、码哈希、是否已使用、使用时间、创建时间），并补齐相应索引
- [ ] T002 同步镜像 `backend/src/test/resources/schema-h2.sql`，使 H2 测试库结构与 `V78__two_factor_auth.sql` 保持一致

## 阶段 2：基础设施

- [ ] T003 `ErrorCode` 枚举新增 8 个错误码：`MFA_REQUIRED`、`MFA_TICKET_INVALID`、`MFA_CODE_INVALID`、`MFA_LOCKED`、`MFA_NOT_ENABLED`、`MFA_ALREADY_ENABLED`、`RECOVERY_CODE_INVALID`、`MFA_SECRET_MISSING`
- [ ] T004 新增配置项 `crm.security.mfa.secret-key` / 环境变量 `MFA_SECRET_KEY`；缺失时 fail closed，报 `MFA_SECRET_MISSING`，禁止硬编码降级
- [ ] T005 `User` 实体扩展 4 个字段，新增 `UserRecoveryCode` 实体，并实现对应的 MyBatis-Plus repository / mapper

## 阶段 3：核心服务

- [ ] T006 实现 `TotpService`（RFC 6238：HMAC-SHA1 / 6 位 / 30 秒步长 / ±1 步容差，使用 `javax.crypto.Mac` + commons-codec Base32），并附官方测试向量单测
- [ ] T007 实现 `SecretEncryptionService`（AES-256-GCM，随机 IV，密文格式 `base64(iv):base64(ct+tag)`），密钥材料取自 T004 配置
- [ ] T008 实现 `QrCodeService`（`com.google.zxing:core` 生成二维码，输出 PNG Base64）
- [ ] T009 实现 `MfaTicketService`（Redis `mfa:ticket:{token}`，TTL 300 秒，一次性消费）
- [ ] T010 实现 `MfaAttemptService`（Redis `mfa:fail:{userId}` INCR，TTL 900 秒，连续满 5 次锁定 15 分钟）
- [ ] T011 实现 `MfaReplayGuard`（Redis `mfa:used:{userId}:{timeStep}`，TTL 90 秒，同一时间步防重放）
- [ ] T012 实现 `RecoveryCodeService`（生成 10 个 8 位 Base32 恢复码，每码独立盐 + SHA-256 哈希落库，明文仅返回一次）

## 阶段 4：后端端点

- [ ] T013 `POST /api/v1/auth/2fa/setup`：生成 Base32 密钥、`otpauth://` 链接与二维码，账号保持未启用状态
- [ ] T014 `POST /api/v1/auth/2fa/enable`：校验动态码通过后置为已启用并一次性返回 10 个恢复码明文
- [ ] T015 `POST /api/v1/auth/2fa/verify`：校验动态码（`code`）或恢复码（`recoveryCode`），签发访问令牌
- [ ] T016 `GET /api/v1/auth/2fa/status`：返回启用状态、绑定时间与剩余恢复码数量
- [ ] T017 `POST /api/v1/auth/2fa/recovery-codes/regenerate`：重新生成恢复码并使旧码全部失效
- [ ] T018 `POST /api/v1/auth/2fa/disable`：凭密码 + 动态码或恢复码关闭 2FA，作废密钥与恢复码并记录审计日志
- [ ] T019 `POST /api/v1/admin/users/{id}/2fa/reset`：管理员重置指定用户 2FA（`user:manage` 权限 + 审计日志）
- [ ] T020 改造 `/api/v1/auth/login`：已启用 2FA 的用户返回 `{mfaRequired, mfaToken, expiresIn: 300}` 且不含 `accessToken`；未启用用户行为逐字节不变

## 阶段 5：前端

- [ ] T021 登录页二次验证视图：输入 6 位动态码，支持切换为恢复码输入
- [ ] T022 绑定向导：扫码/录入密钥 → 输入动态码 → 展示一次性恢复码
- [ ] T023 账户安全页：展示 2FA 状态、剩余恢复码数量，提供关闭与重新生成恢复码入口
- [ ] T024 管理员用户管理页新增「重置 2FA」入口
- [ ] T025 新增中英文 i18n 文案资源

## 阶段 6：测试与回归

- [ ] T026 后端单测：TotpService 官方测试向量、密钥加密/解密、恢复码哈希
- [ ] T027 集成测试：verify 重放拒绝、恢复码重用拒绝、连续 5 次失败锁定、Redis 不可用 fail closed、非 2FA 登录逐字节兼容
- [ ] T028 前端测试：既有 58 项保持全绿，新增二次验证视图测试
- [ ] T029 全量回归：`mvn test`、`pnpm run typecheck`、`pnpm run lint`、`pnpm run test` 全绿

## 依赖关系

- T013 → T006 / T008
- T014 → T012
- T015 → T009 / T010 / T011
- T019 → T003
- T020 → T009
- T021 → T013 / T014 / T015 / T016
- T022 → T013 / T014
- T023 → T016 / T017 / T018
- T024 → T019
