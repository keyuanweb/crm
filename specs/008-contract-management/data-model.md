# Data Model: 合同管理模块

**Branch**: `008-contract-management` | **Date**: 2026-08-22

## 新增实体

### Contract（合同）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| contract_no | VARCHAR(30) | NOT NULL, 唯一（非删除时） | 合同编号 HT-YYYYMMDD-XXXX |
| title | VARCHAR(200) | NOT NULL | 合同标题 |
| customer_id | BIGINT | NOT NULL | 客户 |
| quote_id | BIGINT | NULL | 关联报价单（可选） |
| amount | BIGINT | NOT NULL DEFAULT 0 | 合同金额（分） |
| start_date | DATE | NULL | 生效日期 |
| end_date | DATE | NULL | 结束日期 |
| content | TEXT | NULL | 合同正文（模板生成/手填） |
| status | VARCHAR(30) | NOT NULL DEFAULT 'DRAFT' | DRAFT/PENDING_APPROVAL/APPROVED/EFFECTIVE/COMPLETED/TERMINATED |
| approver_id | BIGINT | NULL | 审批人 |
| approved_at | DATETIME | NULL | 审批时间 |
| reject_reason | VARCHAR(500) | NULL | 拒绝意见 |
| effective_at | DATETIME | NULL | 生效时间 |
| terminated_reason | VARCHAR(500) | NULL | 终止原因 |
| remark | VARCHAR(500) | NULL | 备注 |
| deleted | TINYINT(1) | NOT NULL DEFAULT 0 | 逻辑删除 |
| version | INT | NOT NULL DEFAULT 0 | 乐观锁 |
| created_by | BIGINT | NULL | 创建人 |
| created_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**唯一约束**: `active_key = CONCAT('c||', contract_no)` 非删除唯一，索引 `uk_contract_active_no`。
**索引**: `idx_contract_deleted_customer(deleted, customer_id)`、`idx_contract_deleted_status(deleted, status)`。

### ContractAttachment（合同附件）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| contract_id | BIGINT | NOT NULL | 合同 |
| file_name | VARCHAR(255) | NOT NULL | 原始文件名 |
| file_path | VARCHAR(500) | NOT NULL | 相对存储路径 |
| file_size | BIGINT | NOT NULL | 大小（字节） |
| content_type | VARCHAR(100) | NULL | MIME 类型 |
| uploaded_by | BIGINT | NULL | 上传人 |
| deleted | TINYINT(1) | NOT NULL DEFAULT 0 | 逻辑删除 |
| created_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP | 上传时间 |
| updated_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**: `idx_attachment_contract(contract_id)`。

### ContractTemplate（合同模板）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| name | VARCHAR(100) | NOT NULL | 模板名称 |
| content | TEXT | NOT NULL | 模板正文（含占位符） |
| status | VARCHAR(20) | NOT NULL DEFAULT 'ACTIVE' | ACTIVE/INACTIVE |
| deleted | TINYINT(1) | NOT NULL DEFAULT 0 | 逻辑删除 |
| version | INT | NOT NULL DEFAULT 0 | 乐观锁 |
| created_by | BIGINT | NULL | 创建人 |
| created_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

### 复用既有实体

| 实体 | 用途 |
|---|---|
| Customer | 合同客户 |
| Quote | 合同关联报价（须 APPROVED 且客户一致） |

## 状态流转

```
DRAFT ──提交──▶ PENDING_APPROVAL ──通过──▶ APPROVED ──标记生效──▶ EFFECTIVE ──完成──▶ COMPLETED
   ▲                  │                            │                    └──终止(填原因)──▶ TERMINATED
   └────编辑后重提─────┴──拒绝(填意见)──▶ REJECTED   │
                                                     └─（生效前可标记完成/终止）
```

- DRAFT/REJECTED：可编辑、可提交。
- PENDING_APPROVAL：仅审批人可通过/拒绝；不可编辑。
- APPROVED：可标记生效/完成/终止；不可编辑。
- EFFECTIVE：可标记完成/终止；不可编辑。
- COMPLETED/TERMINATED：终态。
- 非法流转抛 409 CONTRACT_INVALID_STATE。
