# Data Model: 销售仪表盘模块

**Branch**: `006-sales-dashboard` | **Date**: 2026-08-22

## 新增实体

### SalesTarget（销售目标）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| target_month | VARCHAR(7) | NOT NULL, 唯一（非删除时） | 目标月份，YYYY-MM |
| target_amount | BIGINT | NOT NULL | 目标金额（分） |
| deleted | TINYINT(1) | NOT NULL DEFAULT 0 | 逻辑删除 |
| version | INT | NOT NULL DEFAULT 0 | 乐观锁 |
| created_by | BIGINT | NULL | 创建人（管理员） |
| created_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**唯一约束**: 生成列 `active_key = CASE WHEN deleted=0 THEN CONCAT('m||', target_month) ELSE NULL END`，唯一索引 `uk_sales_target_active_month(active_key)`（与 V6 客户唯一约束同模式，保证逻辑删除后同月可重建）。

**索引**: `idx_sales_target_deleted_month(deleted, target_month)`（按月查询走索引）。

### 复用既有实体（只读聚合）

| 实体 | 用途 | 关键字段 |
|---|---|---|
| SalesOpportunity | 漏斗/预测/停滞预警 | stage、amount、expectedCloseDate、closeResult、closedAt、updatedAt、deleted |
| Customer | 客户分析 | status（ACTIVE/INACTIVE）、createdAt、deleted |
| FollowUp | 跟进报表 | method、content、createdAt、deleted |
| User | 最近跟进人显示 | id、displayName/username |

## 聚合口径（只读，不落库）

- **漏斗**: `sales_opportunity WHERE deleted=0` 按 stage 分组 count/sum(amount)，阶段顺序固定 INITIAL_CONTACT→NEGOTIATING→CLOSED_WON→CLOSED_LOST。
- **预测**: 同上数据源，`Σ(amount × stageProbability)`，概率 0.2/0.5/1.0/0.0。
- **业绩达成**: `target_month = 当月` 的目标金额 vs `stage=CLOSED_WON AND closed_at 属于当月 AND deleted=0` 的金额合计。
- **客户分析**: `customer WHERE deleted=0`：总数、`status=ACTIVE` 数、`createdAt 属于当月` 数。
- **跟进报表**: `follow_up WHERE deleted=0`：总数、按 method 分组计数、最近 10 条按 createdAt 倒序。
- **停滞预警**: `stage IN (INITIAL_CONTACT, NEGOTIATING) AND updated_at < now - N天 AND deleted=0`，按停滞天数倒序。

## 状态流转

无状态机（目标为配置数据，仅按月 upsert）。
