# 数据模型：双因素认证（TOTP 2FA）

## 迁移

- Flyway 迁移文件：`V78__two_factor_auth.sql`
  - 在 `user` 表新增 4 列：`two_factor_enabled`、`totp_secret_encrypted`、`two_factor_enabled_at`、`last_2fa_verified_at`。
  - 新建 `user_recovery_code` 表及索引 `idx_urc_user`、`idx_urc_user_used`。
  - 同步镜像到 `backend/src/test/resources/schema-h2.sql`，保证测试用 H2 内存库结构与 MySQL 主库一致（H2 模式下类型做等价映射，如 `TINYINT(1)` → `BOOLEAN`/`TINYINT`，`DATETIME` → `TIMESTAMP`，索引与约束保持一致）。

## user 表新增列

| 列名 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| two_factor_enabled | TINYINT(1) | NOT NULL DEFAULT 0 | 是否启用 2FA（0=未启用，1=已启用） |
| totp_secret_encrypted | VARCHAR(512) | NULL | AES-256-GCM 密文，格式 `base64(iv):base64(ciphertext+tag)`，除 setup 期间外不明文返回 |
| two_factor_enabled_at | DATETIME | NULL | 绑定时间（2FA 启用时刻） |
| last_2fa_verified_at | DATETIME | NULL | 最近一次二次验证时间 |

## user_recovery_code 表

| 列 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | BIGINT | PK AUTO_INCREMENT | 主键 |
| user_id | BIGINT | NOT NULL（FK → user.id，ON DELETE CASCADE） | 所属用户 |
| code_hash | VARCHAR(128) | NOT NULL | 每码独立随机盐 + SHA-256，格式 `base64(salt):hex(sha256)` |
| used | TINYINT(1) | NOT NULL DEFAULT 0 | 是否已使用（0=未用，1=已用） |
| used_at | DATETIME | NULL | 使用时间 |
| created_at | DATETIME | NOT NULL | 创建时间 |

索引：

| 索引名 | 列 |
| --- | --- |
| idx_urc_user | (user_id) |
| idx_urc_user_used | (user_id, used) |

## Redis 键模型

| 键 | 类型 | 值 | TTL | 说明 |
| --- | --- | --- | --- | --- |
| mfa:ticket:{token} | string | userId | 300s | 密码校验通过后的二次验证票据，一次性 |
| mfa:fail:{userId} | string(int) | 失败计数 | 900s | INCR，满 5 锁定 |
| mfa:used:{userId}:{timeStep} | string | 1 | 90s | 防重放已消费时间步 |

## Java 实体映射

- `User` 实体新增 4 字段：
  - `twoFactorEnabled`：`Boolean`，映射 `two_factor_enabled`
  - `totpSecretEncrypted`：`String`，映射 `totp_secret_encrypted`
  - `twoFactorEnabledAt`：`LocalDateTime`，映射 `two_factor_enabled_at`
  - `last2faVerifiedAt`：`LocalDateTime`，映射 `last_2fa_verified_at`
- 新建 `UserRecoveryCode` 实体：`@TableName("user_recovery_code")`，使用 MyBatis-Plus 注解映射字段与主键（`@TableId(type = IdType.AUTO)`、`@TableField`），字段对应：`id`/`userId`/`codeHash`/`used`/`usedAt`/`createdAt`。
- 对应 repository 接口：`UserRecoveryCodeMapper extends BaseMapper<UserRecoveryCode>`；`User` 侧沿用现有 `UserMapper`（新增字段由 MyBatis-Plus 自动映射，无需新增 SQL，除非有按 `two_factor_enabled` 等查询需求）。

## 状态机

2FA 状态及转换：

```
未启用 (not_enabled)
   │  生成密钥（待校验，未提交动态码）
   ▼
待绑定 (pending, 密钥已生成未校验)
   │  输入动态码校验通过 → 写入恢复码
   ▼
已启用 (enabled)
   │  连续 5 次二次验证失败
   ▼
已锁定 (locked, 失败5次)
   │  锁定窗口（15 分钟）过后
   ▼
已启用 (enabled)

已启用 (enabled) ──关闭 2FA（密码 + 动态码/恢复码）或 管理员重置──▶ 已关闭 (disabled)
已关闭 (disabled) ──重新发起绑定──▶ 待绑定 (pending) ──▶ 已启用 (enabled)
```

- `not_enabled`：默认态，`two_factor_enabled = 0` 且无待校验密钥。
- `pending`：密钥已生成并加密落库（或暂存），但尚未通过动态码校验，账号仍 `two_factor_enabled = 0`。
- `enabled`：动态码校验通过，`two_factor_enabled = 1`，写入 `two_factor_enabled_at` 并生成恢复码。
- `locked`：Redis 失败计数满 5 触发的临时锁定（15 分钟），落库状态仍为 `enabled`，锁定仅存在于 Redis 计数中。
- `disabled`：关闭/重置后 `two_factor_enabled = 0`，密钥与恢复码作废。

## 数据一致性约束

- 明文 TOTP 密钥绝不落库：`totp_secret_encrypted` 只存 AES-256-GCM 密文，任何接口（含 `GET`）不返回明文（setup 期间的一次性返回除外）。
- 恢复码只存哈希：`code_hash` 存 `base64(salt):hex(sha256)`，明文恢复码仅在生成响应中返回一次，落库后不可逆。
- `user_id` 外键级联删除：删除用户时 `user_recovery_code` 随 `ON DELETE CASCADE` 自动清理，避免孤儿记录。
- 绑定未完成不得置 `enabled`：只有「生成密钥 → 输入动态码校验通过」两步完成后才将 `two_factor_enabled` 置 1 并写入绑定时间；校验失败或中途放弃保持未启用，且不影响下次登录。
