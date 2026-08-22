# 数据模型：联系人管理模块

**Branch**: `005-contact-management` | **Date**: 2026-08-22

## 1. contact（新增，Flyway V10）

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | |
| customer_id | BIGINT | NOT NULL, FK→customer | 所属客户（必填） |
| name | VARCHAR(50) | NOT NULL | 姓名 |
| title | VARCHAR(50) | NULL | 职位 |
| phone | VARCHAR(30) | NULL | 电话 |
| email | VARCHAR(100) | NULL | 邮箱 |
| role | VARCHAR(30) | NOT NULL DEFAULT 'OTHER' | DECISION_MAKER/INFLUENCER/EVALUATOR/CHAMPION/OTHER |
| remark | VARCHAR(500) | NULL | 备注 |
| deleted | TINYINT | NOT NULL DEFAULT 0 | 逻辑删除（BaseEntity） |
| version | INT | NOT NULL DEFAULT 0 | 乐观锁（BaseEntity） |
| created_by | BIGINT | NULL | （BaseEntity） |
| created_at / updated_at | DATETIME | NOT NULL | （BaseEntity） |

索引：`idx_contact_deleted_customer`（deleted, customer_id）、`idx_contact_deleted_name`（deleted, name）、`idx_contact_deleted_phone`（deleted, phone）。

## 2. 唯一性

- 同一 customer_id 下（name + phone）组合唯一（Service 层 selectCount 校验，重复 → 409 CONTACT_DUPLICATE）。

## 3. 枚举

- 联系人角色 `ContactRole`: DECISION_MAKER / INFLUENCER / EVALUATOR / CHAMPION / OTHER

## 4. 业务规则

- 必须归属未删除客户；客户逻辑删除后其联系人在列表/搜索中不可见（join 过滤）。
- 删除为逻辑删除，不物理移除。
