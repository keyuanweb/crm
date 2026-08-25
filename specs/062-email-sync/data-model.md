# 数据模型：邮件账户与同步记录模块

**Branch**: `062-email-sync` | **Date**: 2026-08-25

## 1. mail_account（邮件账户，Flyway V69）

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | |
| email | VARCHAR(100) | NOT NULL, UNIQUE | 邮箱地址 |
| display_name | VARCHAR(100) | NOT NULL | 显示名 |
| imap_host | VARCHAR(100) | NULL | IMAP 主机 |
| imap_port | INT | NULL | IMAP 端口 |
| smtp_host | VARCHAR(100) | NULL | SMTP 主机 |
| smtp_port | INT | NULL | SMTP 端口 |
| enabled | TINYINT | NOT NULL DEFAULT 1 | 启用 |
| is_default_sender | TINYINT | NOT NULL DEFAULT 0 | 默认发件 |
| created_by | BIGINT | NULL | |
| created_at / updated_at | DATETIME | NOT NULL | |

## 2. mail_sync_record（同步记录）

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | |
| account_id | BIGINT | NOT NULL | 账户 |
| direction | VARCHAR(20) | NOT NULL | INBOUND/OUTBOUND |
| subject | VARCHAR(200) | NULL | 主题 |
| from_address | VARCHAR(100) | NULL | 发件人 |
| to_address | VARCHAR(100) | NULL | 收件人 |
| sync_status | VARCHAR(20) | NOT NULL | SYNCED/FAILED |
| external_id | VARCHAR(100) | NULL | 外部邮件 id（真实对接填充） |
| sync_time | DATETIME | NOT NULL | 同步时间 |

## 3. 业务规则

- 邮箱唯一；邮箱格式校验（422 MAIL_EMAIL_INVALID）。
- 默认发件唯一（新默认自动取消原默认）。
- 模拟同步生成 INBOUND 记录（SYNCED）。
