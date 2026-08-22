# Data Model: 产品与报价模块

**Branch**: `007-product-cpq` | **Date**: 2026-08-22

## 新增实体

### Product（产品）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| code | VARCHAR(50) | NOT NULL, 唯一（非删除时） | 产品编码 |
| name | VARCHAR(100) | NOT NULL | 产品名称 |
| spec | VARCHAR(255) | NULL | 规格 |
| unit | VARCHAR(20) | NULL | 单位（个/套/月等） |
| standard_price | BIGINT | NOT NULL DEFAULT 0 | 标准售价（分） |
| status | VARCHAR(20) | NOT NULL DEFAULT 'ACTIVE' | ACTIVE/INACTIVE |
| deleted | TINYINT(1) | NOT NULL DEFAULT 0 | 逻辑删除 |
| version | INT | NOT NULL DEFAULT 0 | 乐观锁 |
| created_by | BIGINT | NULL | 创建人 |
| created_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**唯一约束**: 生成列 `active_key = CASE WHEN deleted=0 THEN CONCAT('p||', code) ELSE NULL END`，唯一索引 `uk_product_active_code(active_key)`（同 V6 模式）。
**索引**: `idx_product_deleted_status(deleted, status)`、`idx_product_deleted_name(deleted, name)`。

### Quote（报价单）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| quote_no | VARCHAR(30) | NOT NULL, 唯一（非删除时） | 报价单号 Q-YYYYMMDD-XXXX |
| customer_id | BIGINT | NOT NULL | 客户 |
| opportunity_id | BIGINT | NULL | 商机（可选） |
| valid_until | DATE | NULL | 有效期 |
| status | VARCHAR(30) | NOT NULL DEFAULT 'DRAFT' | DRAFT/PENDING_APPROVAL/APPROVED/REJECTED |
| total_amount | BIGINT | NOT NULL DEFAULT 0 | 总额（分） |
| remark | VARCHAR(500) | NULL | 备注 |
| approver_id | BIGINT | NULL | 审批人 |
| approved_at | DATETIME | NULL | 审批时间 |
| reject_reason | VARCHAR(500) | NULL | 拒绝意见 |
| deleted | TINYINT(1) | NOT NULL DEFAULT 0 | 逻辑删除 |
| version | INT | NOT NULL DEFAULT 0 | 乐观锁 |
| created_by | BIGINT | NULL | 创建人 |
| created_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**唯一约束**: 生成列 `active_key = CASE WHEN deleted=0 THEN CONCAT('q||', quote_no) ELSE NULL END`，唯一索引 `uk_quote_active_no(active_key)`。
**索引**: `idx_quote_deleted_customer(deleted, customer_id)`、`idx_quote_deleted_status(deleted, status)`。

### QuoteItem（报价行）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| quote_id | BIGINT | NOT NULL | 报价单 |
| product_id | BIGINT | NULL | 产品（快照引用，可空） |
| product_name | VARCHAR(100) | NOT NULL | 产品名快照 |
| unit_price | BIGINT | NOT NULL | 单价快照（分） |
| quantity | INT | NOT NULL | 数量（≥1） |
| discount | DECIMAL(5,4) | NOT NULL DEFAULT 1 | 折扣 0~1 |
| line_total | BIGINT | NOT NULL | 行小计（分）= 单价×数量×(1-折扣) |
| deleted | TINYINT(1) | NOT NULL DEFAULT 0 | 逻辑删除 |
| created_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**索引**: `idx_quote_item_quote(quote_id)`。

### 复用既有实体

| 实体 | 用途 |
|---|---|
| Customer | 报价客户 |
| Opportunity | 报价关联商机（须属于该客户） |

## 金额与计算规则

- 全部金额单位为分（long）。
- 折扣为实付比例：0.75 表示打 7.5 折（付 75%），1 为全价；范围 0~1，默认 1。
- 行小计 = round(单价 × 数量 × 折扣)。
- 总额 = Σ 行小计。
- 编辑行时由后端重算行小计与总额（前端仅展示）。

## 状态流转

```
DRAFT ──提交──▶ PENDING_APPROVAL ──通过──▶ APPROVED（终态）
   ▲                  │
   └────编辑后重提─────┴──拒绝(填意见)──▶ REJECTED
```

- DRAFT/REJECTED：可编辑、可提交。
- PENDING_APPROVAL：仅审批人可通过/拒绝；不可编辑。
- APPROVED：终态，不可再编辑/审批。
- 非法流转抛 409（如对 PENDING 重复审批、编辑 APPROVED）。
