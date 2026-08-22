# Research: 线索管理模块

**Branch**: `004-lead-management` | **Date**: 2026-08-22

## 1. 线索模型与保留字

**Decision**: 新增 `lead` 表（Flyway V7），实体 `@TableName("lead")` 使用反引号转义 MySQL 保留字。`follow_up` 表新增 `lead_id`（V8），且 `customer_id` 改为可空（V9），保证跟进记录在 customer_id/lead_id 间二选一关联。

**Rationale**: lead 是 MySQL 保留字；跟进复用既有实体避免重复表。

## 2. 线索池与分配

**Decision**: 线索池 = `owner_id IS NULL AND status IN (NEW, WORKING)`；领取 = 当前用户设为 owner 且状态 NEW→WORKING；分配 = 管理员指定 owner（admin 专属操作）。

**Rationale**: spec FR-L06/L07；领取与分配逻辑简单且可独立测试。

## 3. 转化（客户+联系人+商机，单事务）

**Decision**: 转化在单个 `@Transactional` 中：按公司名查重客户（存在则关联，否则新建）→ 联系人按（姓名+电话）查重 → 创建商机（默认阶段 INITIAL_CONTACT，金额来自转化请求）→ 线索置 QUALIFIED 并记录 converted_customer_id/converted_at。重复转化（QUALIFIED）拒绝。

**Rationale**: FR-L08/L09；单事务保证原子性（章程原则三）。

## 4. 状态机

**Decision**: NEW → WORKING（领取/分配/编辑）→ QUALIFIED（转化）或 DISQUALIFIED（标记无效）；QUALIFIED/DISQUALIFIED 为终态，不可编辑核心字段，不可删除。

**Rationale**: FR-L13；终态保护防误操作。

## 5. Excel 导入导出

**Decision**: 复用 Apache POI（客户导入导出同款实现），提供模板下载、批量导入（含错误结果汇总）、按筛选条件导出。

**Rationale**: FR-L11；复用既有工具类避免重复造轮子。
