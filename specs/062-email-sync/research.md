# Research: 邮件账户与同步记录模块

**Branch**: `062-email-sync` | **Date**: 2026-08-25

## 1. 账户模型

**Decision**: `mail_account` 表（email 唯一、display_name、imap_host/port、smtp_host/port、enabled、is_default_sender、created_by/at）。默认发件唯一：设置新默认时先清除其他账户默认标记。

**Rationale**: 账户参数化存储（真实 IMAP/SMTP 对接时读取）；默认发件唯一保证发件路由确定。

## 2. 同步记录与模拟同步

**Decision**: `mail_sync_record` 表（account_id、direction INBOUND/OUTBOUND、subject、from_address/to_address、sync_status SYNCED/FAILED、external_id 占位、sync_time）。模拟同步端点 `POST /mail-accounts/{id}/sync` 生成一条 INBOUND 记录（SYNCED，external_id 模拟）。

**Rationale**: 记录结构含外部邮件 id 占位，真实对接填充；模拟同步完整验证 账户→同步→记录 链路。

## 3. 凭证与安全

**Decision**: v1 不存储邮箱密码明文（仅参数）；真实 OAuth token 留外部密钥管理。邮箱格式用简单正则校验。

**Rationale**: 避免明文凭证存储；校验防录入错误。
