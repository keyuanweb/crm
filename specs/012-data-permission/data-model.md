# Data Model: 数据权限增强模块

**Branch**: `012-data-permission` | **Date**: 2026-08-22

## 新增实体

### Department（部门）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| name | VARCHAR(50) | NOT NULL | 部门名称 |
| parent_id | BIGINT | NULL | 上级部门（空=顶级） |
| deleted | TINYINT(1) | NOT NULL DEFAULT 0 | 逻辑删除 |
| version | INT | NOT NULL DEFAULT 0 | 乐观锁 |
| created_by | BIGINT | NULL | 创建人 |
| created_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**索引**: `idx_department_parent(parent_id)`。

### CustomerShare（客户共享）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| customer_id | BIGINT | NOT NULL | 客户 |
| shared_to_user_id | BIGINT | NOT NULL | 共享给用户 |
| shared_by | BIGINT | NULL | 共享人 |
| deleted | TINYINT(1) | NOT NULL DEFAULT 0 | 逻辑删除 |
| created_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP | 更新时间 |
| active_key | VARCHAR(64) | 生成列 | customer_id + shared_to_user_id 非删除唯一 |

**索引**: `idx_share_customer(customer_id)`、`idx_share_to_user(shared_to_user_id)`。

## 修改既有实体

### User — 新增字段

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| department_id | BIGINT | NULL | 所属部门 |
| data_scope | VARCHAR(20) | NOT NULL DEFAULT 'SELF' | SELF/DEPT/DEPT_AND_CHILD/ALL |

## 权限解析规则

- `SELF` → owner ∈ {本人}
- `DEPT` → owner ∈ {本部门成员 id}
- `DEPT_AND_CHILD` → owner ∈ {本部门及子孙部门成员 id}
- `ALL`（ADMIN 强制）→ 不过滤（含公海）
- 公海（owner 空）走 011 公海视图，不进入权限过滤列表。
