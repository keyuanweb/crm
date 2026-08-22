# Research: 客户公海与转移模块

**Branch**: `011-customer-pool` | **Date**: 2026-08-22

## 1. 归属字段与数据迁移

**Decision**: `customer` 表新增 `owner_id BIGINT NULL`（V23 迁移 + `idx_customer_deleted_owner(deleted, owner_id)` 索引）；`Customer` 实体新增 `ownerId` 字段。存量客户保持 owner 为空（进公海）或由管理员后续分配；不做历史数据回填。

**Rationale**: 最小改动；owner 空即公海，与 spec 一致。

## 2. 公海查询与领取

**Decision**: 公海列表 = `owner_id IS NULL AND deleted=0`（分页，关键字/状态筛选）；领取 = 事务内 `UPDATE customer SET owner_id=? WHERE id=? AND owner_id IS NULL`，影响行数 0 则 409 CUSTOMER_ALREADY_OWNED（防并发重复领取）。"我的客户" = `owner_id = 当前用户`。

**Rationale**: 条件更新保证原子领取（并发安全），无需乐观锁；与既有逻辑删除/乐观锁并存（owner 更新不依赖 version，避免频繁冲突）。

## 3. 超期退回（公海扫描）

**Decision**: 手动扫描（仅 ADMIN）：计算每客户"最近活跃时间"= MAX(follow_up.created_at where customer_id) 或（无跟进时）customer.created_at；活跃时间 < now - N 天（N=`crm.pool.stale-days` 默认 30）且 owner 非空 → 清空 owner_id（UpdateWrapper 条件 owner_id IS NOT NULL）。返回扫描结果（退回条数）。批次查询 follow_up 避免 N+1。

**Rationale**: 手动触发（spec 假设，无定时任务）；超期判定按活跃时间（有跟进按跟进，无跟进按创建）；批量 UPDATE 减少 IO。

## 4. 批量转移与分配

**Decision**: `POST /customers/batch-transfer`（仅 ADMIN）：body 含 `customerIds`（≤100）、`targetOwnerId`（目标用户须存在 404 USER_NOT_FOUND）；单事务内批量 `UPDATE customer SET owner_id=target WHERE id IN (...)`（含公海分配：target 非空即归属）。返回实际更新条数。

**Rationale**: 单一接口覆盖"转移"与"分配"（目标非空即分配）；批量上限防滥用。

## 5. 审计轨迹

**Decision**: 领取/扫描退回（逐条或汇总）/批量转移均 `auditService.record`：领取（CLAIM，CUSTOMER，id，"领取客户"）、退回（POOL_RETURN，"退回公海"）、转移（TRANSFER，"转移至用户X"）。批量按批次记录汇总 + 逐条明细（简化为批次汇总 + 首条示例）。

**Rationale**: 复用既有审计表与模式（FR-PL08）；批量记录汇总避免日志爆炸。

## 6. 契约与权限

**Decision**: 契约写入 `contracts/customer-pool.md`。公海列表/领取：所有登录用户；扫描与批量转移：仅 ADMIN（方法级）。客户列表页扩展"我的/公海"视图。

**Rationale**: 与既有权限模式一致；服务端强制授权（章程原则三）。
