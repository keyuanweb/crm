# 数据模型：线索管理模块

**Branch**: `004-lead-management` | **Date**: 2026-08-22

## 1. lead（新增，Flyway V7）

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | |
| name | VARCHAR(50) | NOT NULL | 姓名 |
| company | VARCHAR(100) | NOT NULL | 公司 |
| title | VARCHAR(50) | NULL | 职位 |
| phone | VARCHAR(30) | NULL | 电话 |
| email | VARCHAR(100) | NULL | 邮箱 |
| source | VARCHAR(20) | NOT NULL DEFAULT 'OTHER' | WEBSITE/AD/EXHIBITION/REFERRAL/COLD_CALL/OTHER |
| status | VARCHAR(20) | NOT NULL DEFAULT 'NEW' | NEW/WORKING/QUALIFIED/DISQUALIFIED |
| score | INT | NOT NULL DEFAULT 0 | 评分 0-100 |
| owner_id | BIGINT | NULL | 负责人（空=线索池） |
| converted_customer_id | BIGINT | NULL | 转化后关联客户 |
| converted_at | DATETIME | NULL | 转化时间 |
| campaign_id | BIGINT | NULL | 营销归因活动（014 增列，V33） |
| remark | VARCHAR(500) | NULL | 备注 |
| deleted | TINYINT | NOT NULL DEFAULT 0 | 逻辑删除（BaseEntity） |
| version | INT | NOT NULL DEFAULT 0 | 乐观锁（BaseEntity） |
| created_by | BIGINT | NULL | （BaseEntity） |
| created_at / updated_at | DATETIME | NOT NULL | （BaseEntity） |

索引：`idx_lead_deleted_owner`（deleted, owner_id）、`idx_lead_deleted_status`（deleted, status）、`idx_lead_deleted_source`（deleted, source）、`idx_lead_campaign`（campaign_id, 014 增）。

## 2. follow_up（扩展，Flyway V8/V9）

| 列 | 变更 |
|---|---|
| lead_id | 新增（可空，与 customer_id 二选一） |
| customer_id | 改为可空（原 NOT NULL） |

## 3. 状态机

```
NEW ──领取/分配/编辑──> WORKING ──转化──> QUALIFIED（终态）
                              └─标记无效──> DISQUALIFIED（终态）
```

- QUALIFIED/DISQUALIFIED 不可编辑核心字段、不可删除。
- 转化单事务创建：客户（按公司名查重）→ 联系人（按姓名+电话查重）→ 商机（默认 INITIAL_CONTACT）。

## 4. 枚举

- 线索来源 `LeadSource`: WEBSITE / AD / EXHIBITION / REFERRAL / COLD_CALL / OTHER
- 线索状态 `LeadStatus`: NEW / WORKING / QUALIFIED / DISQUALIFIED
