# Data Model: 客户公海与转移模块

**Branch**: `011-customer-pool` | **Date**: 2026-08-22

## 修改既有实体

### Customer（客户）— 新增字段

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| owner_id | BIGINT | NULL | 归属销售（空 = 公海） |

**迁移**: V23 `ALTER TABLE customer ADD COLUMN owner_id BIGINT NULL COMMENT '归属销售'` + `KEY idx_customer_deleted_owner (deleted, owner_id)`。

## 查询口径

- **公海列表**: `owner_id IS NULL AND deleted = 0`（分页，关键字/状态筛选）。
- **我的客户**: `owner_id = 当前用户 AND deleted = 0`。
- **超期判定（扫描）**: 每客户活跃时间 = MAX(follow_up.created_at WHERE customer_id)（无跟进则 customer.created_at）；活跃时间 < now - N 天（N 默认 30，`crm.pool.stale-days`）且 owner 非空 → 退回。
- **批量转移/分配**: `UPDATE customer SET owner_id = target WHERE id IN (...)`；目标用户须存在。

## 状态与权限

- 领取：owner 空 → 本人（条件更新，并发安全）。
- 扫描/批量转移：仅 ADMIN。
- 领取/退回/转移：记审计（CLAIM/POOL_RETURN/TRANSFER）。
