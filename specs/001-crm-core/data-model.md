# Data Model: CRM 初始版本

**Branch**: `001-crm-core` | **Date**: 2026-08-21 | **Spec**: [spec.md](./spec.md) | **Research**: [research.md](./research.md)

> Phase 1 输出。实体与字段以业务语义描述，落库命名遵循 snake_case；所有主表含
> `deleted`（逻辑删除，0/1）与 `version`（乐观锁）字段；时间字段统一
> `created_at` / `updated_at`（`DATETIME`）；金额单位：分（`BIGINT`），避免浮点误差。

## 实体总览与关系

```text
User (1) ───< FollowUp.follow_up_by
Customer (1) ───< Opportunity (1) ───< SalesOpportunity
Customer (1) ───< FollowUp.customer_id
Opportunity (1) ───< FollowUp.opportunity_id (可选)
```

- 一个客户可拥有多个商机；一个商机可产生多个销售机会（Q1 选项 C）；
- 跟进记录必须关联客户，可额外关联商机（规格假设）。

## 1. User（用户）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT PK | 自增 | |
| username | VARCHAR(50) | 唯一、必填 | 登录名（默认 admin） |
| password_hash | VARCHAR(100) | 必填 | bcrypt 哈希，绝不存明文（章程原则三） |
| display_name | VARCHAR(50) | 必填 | 显示名 |
| role | VARCHAR(20) | 必填，枚举 | ADMIN / SALES / SUPPORT |
| enabled | TINYINT(1) | 默认 1 | 是否可用 |
| created_at / updated_at | DATETIME | | |

## 2. Customer（客户）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT PK | 自增 | |
| name | VARCHAR(100) | 必填 | 客户名称（搜索字段） |
| company | VARCHAR(100) | 必填 | 公司（搜索字段） |
| contact_person | VARCHAR(50) | 可选 | 联系人 |
| phone | VARCHAR(30) | 格式校验 | 电话（搜索字段） |
| email | VARCHAR(100) | 格式校验 | 邮箱 |
| address | VARCHAR(255) | 可选 | 地址 |
| remark | VARCHAR(500) | 可选 | 备注 |
| status | VARCHAR(20) | 默认 ACTIVE | ACTIVE / INACTIVE（FR-001 筛选条件） |
| deleted | TINYINT(1) | 默认 0 | 逻辑删除（FR-005） |
| version | INT | | 乐观锁 |
| created_by | BIGINT | | 创建人（User.id） |

**校验**（FR-003）：name/company 必填；phone 匹配 `^[0-9+\-() ]{5,30}$`；email 匹配标准格式；name+company 组合去重（同 deleted=0 范围内唯一）。

## 3. Opportunity（商机，父实体）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT PK | 自增 | |
| customer_id | BIGINT FK | 必填 | → Customer.id（FR-009） |
| name | VARCHAR(100) | 必填 | 商机名称（FR-007 搜索字段） |
| expected_amount_min | BIGINT | ≥0 | 预期金额下限（分） |
| expected_amount_max | BIGINT | ≥0 且 ≥min | 预期金额上限（分） |
| remark | VARCHAR(500) | 可选 | 备注 |
| status | VARCHAR(20) | 默认 ACTIVE | ACTIVE / ARCHIVED（FR-007 筛选） |
| deleted / version / created_by / created_at / updated_at | | | 同上 |

**校验**（FR-009）：customer_id 必须存在且未删除；`0 ≤ min ≤ max ≤ 99,999,999,999`（上限 10 亿元）。

## 4. SalesOpportunity（销售机会，子实体）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT PK | 自增 | |
| opportunity_id | BIGINT FK | 必填 | → Opportunity.id（FR-012，一个商机多个销售机会） |
| amount | BIGINT | ≥0 | 金额（分） |
| stage | VARCHAR(30) | 必填，枚举 | INITIAL_CONTACT → NEGOTIATING → CLOSED_WON / CLOSED_LOST |
| expected_close_date | DATE | 可选 | 预计成交日期 |
| close_result | VARCHAR(20) | 关闭时必填 | WON / LOST |
| closed_at | DATETIME | 关闭时写入 | |
| deleted / version / created_by / created_at / updated_at | | | 同上 |

**校验**（FR-009/FR-014）：opportunity_id 必须存在且未删除；amount 范围 `0 ≤ amount ≤ 99,999,999,999`；关闭动作必须提供 close_result（WON/LOST），关闭后 stage 置为终态且不得再流转。

## 5. FollowUp（跟进记录）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT PK | 自增 | |
| customer_id | BIGINT FK | 必填 | → Customer.id（FR-015） |
| opportunity_id | BIGINT FK | 可选 | → Opportunity.id |
| method | VARCHAR(20) | 必填，枚举 | PHONE / EMAIL / MEETING / OTHER |
| content | VARCHAR(2000) | 必填 | 跟进内容 |
| next_follow_up_at | DATETIME | 可选 | 下次跟进时间 |
| follow_up_by | BIGINT | 必填 | → User.id（跟进人） |
| created_at / updated_at | DATETIME | | |

**校验**（FR-015）：customer_id 必填；content 非空且 ≤2000 字；method 枚举；opportunity_id 若提供必须属于同一 customer（跨实体一致性校验）。

## 6. AuditLog（审计日志，FR-017）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT PK | 自增 | |
| actor_id / actor_name | BIGINT / VARCHAR(50) | 可选 | 操作人（可能为系统） |
| action | VARCHAR(50) | 必填 | CREATE / UPDATE / DELETE / IMPORT / EXPORT / CLOSE |
| entity_type | VARCHAR(50) | 必填 | CUSTOMER / OPPORTUNITY / SALES_OPPORTUNITY |
| entity_id | BIGINT | 可选 | 对象标识 |
| detail | VARCHAR(500) | 可选 | 补充说明（**不落敏感字段明文**） |
| created_at | DATETIME | | |

**说明**：仅记录操作元数据；写入失败不阻塞主流程；脱敏约定见 contracts/customers.md（FR-016：列表脱敏、详情完整）。

## 状态机

### SalesOpportunity.stage（核心流转）

```text
        ┌───────────────┐
        ▼               │ (编辑，未关闭前可回退)
INITIAL_CONTACT ──► NEGOTIATING
        │               │
        │ close(WON)    │ close(LOST)
        ▼               ▼
   CLOSED_WON ◄── 终态 ──► CLOSED_LOST
```

- 流转规则：仅允许 `INITIAL_CONTACT → NEGOTIATING → (CLOSED_WON | CLOSED_LOST)`；终态不可再流转或编辑（FR-014"关闭后不再出现在活动管道"）；关闭必须写 close_result 与 closed_at。
- 活动管道 = `stage IN (INITIAL_CONTACT, NEGOTIATING) AND deleted = 0`（FR-012/FR-014）。

### Customer.status / Opportunity.status

- `ACTIVE → INACTIVE`（客户停用，不影响历史商机/跟进）；`ACTIVE → ARCHIVED`（商机归档）。
- 逻辑删除（deleted 0→1）独立于状态，任何状态均可删除；删除后不出现在列表/搜索/统计（FR-005/SC-005）。

## 索引设计（满足 SC-002）

| 表 | 索引 | 目的 |
|---|---|---|
| customer | (deleted, company), (deleted, name), (deleted, phone) | 列表搜索/筛选 |
| opportunity | (deleted, customer_id), (deleted, name) | 按客户/名称筛选 |
| sales_opportunity | (deleted, stage), (deleted, opportunity_id) | 阶段管道列表、统计聚合 |
| follow_up | (customer_id, created_at), (opportunity_id) | 时间线查询 |

## 一致性约束（章程原则三）

- 外键关联字段（customer_id / opportunity_id）不允许指向已删除记录；
- 事务边界：创建/编辑/关闭操作在 Service 层 `@Transactional`；导入分批事务（每 500 行一批，R6）；
- 统计与列表共享同一数据源，保证 SC-006 一致。
