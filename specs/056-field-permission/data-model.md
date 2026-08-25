# 数据模型：字段级读写权限模块

**Branch**: `056-field-permission` | **Date**: 2026-08-25

## 1. field_permission（字段权限，Flyway V64）

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | |
| role_id | BIGINT | NOT NULL | 角色 |
| entity_type | VARCHAR(20) | NOT NULL | LEAD/CUSTOMER/OPPORTUNITY/TICKET |
| field_id | BIGINT | NOT NULL | 自定义字段 id |
| permission | VARCHAR(20) | NOT NULL | HIDDEN/READ_ONLY/EDITABLE |
| created_by | BIGINT | NULL | |
| created_at / updated_at | DATETIME | NOT NULL | |

唯一约束：`uk_field_perm`（role_id, entity_type, field_id）。

## 2. 业务规则

- 未配置 → EDITABLE（默认）。
- ADMIN 角色恒 EDITABLE（豁免）。
- 保存校验：HIDDEN 字段写入 422；READ_ONLY 字段修改已存在值 422。
- 配置仅 ADMIN。
