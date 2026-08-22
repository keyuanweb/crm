# Data Model: 订单与回款模块

**Branch**: `009-order-payment` | **Date**: 2026-08-22

## 新增实体

### SalesOrder（订单）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| order_no | VARCHAR(30) | NOT NULL, 唯一（非删除时） | 订单号 SO-YYYYMMDD-XXXX |
| title | VARCHAR(200) | NOT NULL | 订单标题 |
| customer_id | BIGINT | NOT NULL | 客户 |
| contract_id | BIGINT | NULL | 关联合同（可选，须生效） |
| amount | BIGINT | NOT NULL DEFAULT 0 | 订单金额（分） |
| status | VARCHAR(20) | NOT NULL DEFAULT 'PENDING' | PENDING/PARTIAL/PAID |
| description | VARCHAR(500) | NULL | 说明 |
| deleted | TINYINT(1) | NOT NULL DEFAULT 0 | 逻辑删除 |
| version | INT | NOT NULL DEFAULT 0 | 乐观锁 |
| created_by | BIGINT | NULL | 创建人 |
| created_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**唯一约束**: `active_key = CONCAT('o||', order_no)` 非删除唯一，索引 `uk_order_active_no`。
**索引**: `idx_order_deleted_customer(deleted, customer_id)`、`idx_order_deleted_status(deleted, status)`。

### PaymentPlan（回款计划期次）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| order_id | BIGINT | NOT NULL | 订单 |
| seq_no | INT | NOT NULL | 期次序号 |
| amount | BIGINT | NOT NULL | 应收金额（分） |
| due_date | DATE | NOT NULL | 计划回款日期 |
| description | VARCHAR(200) | NULL | 期次说明 |
| status | VARCHAR(20) | NOT NULL DEFAULT 'PENDING' | PENDING/PARTIAL/PAID |
| deleted | TINYINT(1) | NOT NULL DEFAULT 0 | 逻辑删除 |
| created_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |

**索引**: `idx_plan_order(order_id)`。

### PaymentRecord（回款记录）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| plan_id | BIGINT | NOT NULL | 回款计划 |
| order_id | BIGINT | NOT NULL | 订单 |
| amount | BIGINT | NOT NULL | 回款金额（分，>0） |
| paid_at | DATE | NOT NULL | 回款日期 |
| method | VARCHAR(20) | NOT NULL DEFAULT 'TRANSFER' | 转账/现金/支票/其他 |
| recorded_by | BIGINT | NULL | 登记人 |
| created_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP | 登记时间 |

**索引**: `idx_record_plan(plan_id)`、`idx_record_order(order_id)`。

### 复用既有实体

| 实体 | 用途 |
|---|---|
| Customer | 订单客户 |
| Contract | 订单关联合同（须 EFFECTIVE） |

## 状态流转（自动驱动）

- **期次状态**：PENDING →（登记回款累计<应收）PARTIAL →（累计==应收）PAID。
- **订单状态**：PENDING →（任一期末回款）PARTIAL →（全部期次 PAID）PAID。
- 状态仅由回款登记重算，不做手工修改接口。

## 台账口径

- 每期：应收=plan.amount；已收=Σ(payment_record where plan_id)；未收=应收-已收。
- 提醒标识：PAID→"已回款"；未全额回款且 due_date<今天→"逾期"+逾期天数；due_date∈[今天,今天+3]→"临期"；否则"正常"。
- 逾期天数 = 今天 - due_date（自然日）。
