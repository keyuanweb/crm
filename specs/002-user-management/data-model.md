# 数据模型：用户管理模块

**Branch**: `002-user-management` | **Date**: 2026-08-22

## 1. user（扩展，Flyway V4）

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | |
| username | VARCHAR(50) | NOT NULL, UNIQUE | 登录名（创建后不可修改） |
| password_hash | VARCHAR(100) | NOT NULL | bcrypt 哈希 |
| display_name | VARCHAR(50) | NOT NULL | 显示名 |
| role | VARCHAR(20) | NOT NULL | ADMIN / SALES / SUPPORT |
| enabled | TINYINT | NOT NULL DEFAULT 1 | 启用状态 |
| last_login_at | DATETIME | NULL | 最后登录时间（新增） |
| token_version | INT | NOT NULL DEFAULT 0 | 令牌版本（新增；修改密码/重置时 +1） |
| deleted | TINYINT | NOT NULL DEFAULT 0 | 逻辑删除（BaseEntity） |
| version | INT | NOT NULL DEFAULT 0 | 乐观锁（BaseEntity） |
| created_by | BIGINT | NULL | 创建人（BaseEntity） |
| created_at / updated_at | DATETIME | NOT NULL | （BaseEntity） |

索引：`uk_user_username`（username）唯一。

## 2. 令牌模型（JWT）

- Claims：`sub`（userId）、`username`、`role`、`tv`（tokenVersion）。
- 认证过滤器校验：令牌签名 → 用户存在且 enabled=1 → `tv` 与库内一致。
- 密码修改（本人）/ 重置（管理员）→ `token_version+1` → 旧令牌全部失效。

## 3. 枚举

- 角色 `Role`: ADMIN / SALES / SUPPORT

## 4. 业务规则

- 禁止停用/删除当前登录账号。
- 系统至少保留一个启用 ADMIN（停用/改角色前校验）。
- 用户名规则：3~50 位字母/数字/下划线。
- 密码规则：8~64 位且同时包含字母与数字。
