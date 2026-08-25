# 数据模型：开放平台模块

**Branch**: `055-open-platform` | **Date**: 2026-08-25

## 1. api_key（API Key，Flyway V63）

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | |
| name | VARCHAR(100) | NOT NULL | 名称 |
| key_hash | VARCHAR(128) | NOT NULL, UNIQUE | Key 哈希（SHA-256） |
| key_prefix | VARCHAR(16) | NOT NULL | 前缀（展示） |
| scopes | TEXT | NULL | 权限范围 JSON（如 ["customer:read"]） |
| expires_at | DATETIME | NULL | 有效期（空=永不过期） |
| status | VARCHAR(20) | NOT NULL DEFAULT 'ACTIVE' | ACTIVE/REVOKED |
| last_used_at | DATETIME | NULL | 最后使用 |
| use_count | BIGINT | NOT NULL DEFAULT 0 | 使用次数 |
| created_by | BIGINT | NULL | |
| created_at / updated_at | DATETIME | NOT NULL | |

## 2. webhook_subscription（Webhook 订阅）

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | |
| event_type | VARCHAR(50) | NOT NULL | LEAD_CREATED/... |
| callback_url | VARCHAR(500) | NOT NULL | 回调 URL |
| secret | VARCHAR(64) | NOT NULL | 签名密钥 |
| enabled | TINYINT | NOT NULL DEFAULT 1 | 启用 |
| created_by | BIGINT | NULL | |
| created_at / updated_at | DATETIME | NOT NULL | |

## 3. webhook_delivery（推送记录）

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | |
| subscription_id | BIGINT | NOT NULL | |
| event_type | VARCHAR(50) | NOT NULL | |
| entity_type | VARCHAR(30) | NULL | |
| entity_id | BIGINT | NULL | |
| payload | TEXT | NOT NULL | 负载 JSON |
| status | VARCHAR(20) | NOT NULL | SUCCESS/FAILED |
| http_status | INT | NULL | 回调响应码 |
| error | VARCHAR(500) | NULL | 错误 |
| retry_count | INT | NOT NULL DEFAULT 0 | 重试次数 |
| created_at | DATETIME | NOT NULL | |

## 4. 业务规则

- Key 仅创建时返回完整值；列表显前缀（`ck_` + 前 8 位）。
- /api/v1/open/** 需有效 `X-API-Key`（未吊销、未过期）。
- Webhook 签名：`X-Signature` = HMAC-SHA256(secret, 请求体)。推送失败重试 ≤3 次退避（1s/5s/30s）。
