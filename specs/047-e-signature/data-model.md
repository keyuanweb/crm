# 数据模型：电子签署模块

**Branch**: `047-e-signature` | **Date**: 2026-08-25

## 1. signature_record（签署记录，Flyway V58）

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | |
| business_type | VARCHAR(20) | NOT NULL | QUOTE / CONTRACT |
| business_id | BIGINT | NOT NULL | 报价/合同 id |
| signer_id | BIGINT | NOT NULL | 签署人（用户 id） |
| signer_name | VARCHAR(50) | NULL | 签署人姓名（冗余显示） |
| signature_image | MEDIUMTEXT | NOT NULL | 签名图 base64 |
| created_at | DATETIME | NOT NULL | 签署时间 |

索引：`uk_signature_business`（business_type, business_id）唯一——同一单据仅一次签署。

## 2. quote（扩展状态）

| 列 | 变更 |
|---|---|
| status | 枚举 + SIGNED（APPROVED→SIGNED，终态） |

## 3. contract（扩展状态）

| 列 | 变更 |
|---|---|
| status | 枚举 + SIGNED（APPROVED→SIGNED→EFFECTIVE） |

## 4. 状态机

```
Quote: DRAFT → PENDING_APPROVAL → APPROVED → SIGNED(终态)
              └── REJECTED
Contract: DRAFT → PENDING_APPROVAL → APPROVED → SIGNED → EFFECTIVE → COMPLETED
              └── REJECTED                              └── TERMINATED
```

## 5. 业务规则

- 仅 APPROVED 可发起签署（DRAFT/PENDING/REJECTED → 422 SIGNATURE_STATE_INVALID）。
- 同一单据仅一次签署（409 SIGNATURE_ALREADY_SIGNED）。
- 签名图 base64 必填、≤500KB。
- 合同未签署（非 SIGNED）不可生效（422 CONTRACT_INVALID_STATE）。
- 报价 SIGNED 后不可编辑；合同 SIGNED 后可生效/终止。
