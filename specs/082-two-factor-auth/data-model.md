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

---

## ⚠️ 订正（2026-09-16，**事后按现状订正**）

**性质说明**：本文件写于 2026-09-12，此后 13 批落地。以下按**今天的实测现状**订正，
**属"事后按现状订正"（照 090/094/095 的先例）**。原文一律逐字保留在上方、不删改。

### 一、迁移号 `V78` → **`V89`**

同 `plan.md` 订正块 §一：`V78` 已被 `V78__sla_escalation.sql`（1.3-sla-escalation 批）占用，
当前最高 `V88`。实际文件为 **`V89__two_factor_auth.sql`**。

### 二、Redis 键族 `mfa:*` → **`auth:2fa-*`**

**原文（保留）**：`## Redis 键模型` 三行 `mfa:ticket:{token}` / `mfa:fail:{userId}` / `mfa:used:{userId}:{timeStep}`。

**订正**：改为 **`auth:2fa-ticket:` / `auth:2fa-fail:` / `auth:2fa-used:`**，与仓内既有的
`auth:refresh:` / `auth:fail:` / `auth:ip-fail:` / `auth:captcha:` / `auth:user-state:` 同族。
**类型、值、TTL 三列一字不改**（300s / 900s / 90s）。

### 三、`ON DELETE CASCADE` 在软删除下**永不触发**（如实记）

**原文（保留）**：`## user_recovery_code 表` 的 `user_id` 行「NOT NULL（FK → user.id，**ON DELETE CASCADE**）」；
`## 数据一致性约束`「删除用户时 `user_recovery_code` 随 `ON DELETE CASCADE` 自动清理，避免孤儿记录」。

**订正**：约束照建（`V73`/`V74` 已有同形先例），但**要如实说明它在本仓不会触发**：
`User extends BaseEntity`，而 `BaseEntity` 的 `deleted` 字段带 **`@TableLogic`** ——
用户的"删除"是**软删除**（`UPDATE ... SET deleted=1`），**从不产生物理 `DELETE`**，
故 MySQL 的 `ON DELETE CASCADE` **没有触发的机会**。
⇒ 恢复码真正的清理路径是**应用层**：关闭 2FA / 管理员重置时调 `RecoveryCodeService.revokeAll(userId)`。
外键在此的角色是**防御性的**（若有人手工跑物理 `DELETE` 时不至于留下孤儿行），
**不是**"一致性靠它保证"。这一条写下来，是为了避免下一位读者把"有 CASCADE"读成"孤儿已有人管"。

### 四、H2 测试镜像**故意不含任何外键**（本批照旧）

**原文（保留）**：`## 迁移`「同步镜像到 `backend/src/test/resources/schema-h2.sql`，保证测试用 H2 内存库结构与 MySQL 主库一致」；
「索引与约束保持一致」。

**实测**：`backend/src/test/resources/schema-h2.sql` 里 `FOREIGN KEY` 出现 **0 次**
（而主迁移链里有 12 处，分布在 `V71`/`V73`/`V74`）—— 即**镜像在这一点上历来就与主库不一致**，
是**既有设计**而非本批引入。⇒ 本批**照旧**：`user_recovery_code` 的 FK 只写进 `V89`，
镜像里**不写**，`SchemaParityIT` 的门禁也**只核表名**（见 `plan.md` 的测试面事实）。
"结构与主库一致"这句原文在**外键与列类型**两个维度上**自古就是近似**，本批不改变这个近似，
也不假装它精确。

### 五、`totp_secret_encrypted` 的长度 `VARCHAR(512)` 够用（核算过）

**原文（保留）**：`## user 表新增列` 的 `totp_secret_encrypted | VARCHAR(512)`。

**核算**：32 字节密钥经 AES-256-GCM 加密后密文 32 字节 + tag 16 字节 = 48 字节，
`base64(iv)` 12 字节 → 16 字符（去 padding）、`base64(ct||tag)` 48 字节 → 64 字符，
加分隔符 `:` ⇒ **约 81 字符**。`VARCHAR(512)` 有 6 倍余量，**不改**。
（此处只是把原文没写的核算补上，结论与原文一致。）

### 六、`user_recovery_code` 的列清单**漏了 BaseEntity 的三列**（第 1 步实施时发现）

**原文（保留）**：`## user_recovery_code 表` 的列清单是
`id` / `user_id` / `code_hash` / `used` / `used_at` / `created_at` —— **六列**。

**订正**：实际落库是 **九列**，缺的三列是 **`updated_at` / `deleted` / `version`**。
理由是 `## Java 实体映射` 自己写的「新建 `UserRecoveryCode` 实体」（本仓 50 个实体一律
`extends BaseEntity`，该实体也不例外），而 `BaseEntity` 声明了这四列：

| 列 | 为什么**不能省** |
|---|---|
| `deleted` | `@TableLogic` 会把它加进**每一条** MyBatis-Plus 生成的 SELECT 的 `WHERE` ⇒ 缺列报 `Unknown column 'deleted'` |
| `version` | `@Version` 会把它加进**每一条** INSERT/UPDATE 的列清单 |
| `updated_at` | `@TableField(fill = INSERT_UPDATE)` 同上 |

**这不是理论风险，本仓刚修过一次同类缺陷**：`V88__quota_child_tables_base_entity_columns.sql`
补的正是三张 quota 子表缺这三列的问题，其注释记录了用户可见形态 ——
`PUT /api/v1/sales-quota/{id}` 与 `POST /{id}/breakdown` **在生产库上双双 5xx**
（不只是测试库）。故本批在 `V89` 里一次性把九列建全。

**并且补了一道门禁**：新建 `TwoFactorSchemaMappingIT` 做**列级**的写-读往返断言。
原计划没有它 —— 因为 `SchemaParityIT` 只核表名、**明文声明不覆盖列级漂移**，
仅靠它无法拦住这一类缺陷（`plan.md` 的测试面事实里已记其边界）。
