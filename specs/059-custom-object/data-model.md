# 数据模型：自定义对象模块

**Branch**: `059-custom-object` | **Date**: 2026-08-25

## 1. custom_object（自定义对象，Flyway V67）

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | |
| name | VARCHAR(100) | NOT NULL | 对象名称 |
| code | VARCHAR(50) | NOT NULL, UNIQUE | 对象编码 |
| fields | TEXT | NOT NULL | 字段集 JSON |
| enabled | TINYINT | NOT NULL DEFAULT 1 | 启用 |
| deleted | TINYINT | NOT NULL DEFAULT 0 | 逻辑删除 |
| version | INT | NOT NULL DEFAULT 0 | 乐观锁 |
| created_by | BIGINT | NULL | |
| created_at / updated_at | DATETIME | NOT NULL | |

## 2. custom_object_record（对象记录）

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | |
| object_id | BIGINT | NOT NULL | 对象 |
| values | TEXT | NOT NULL | 记录值 JSON 键值对 |
| created_by | BIGINT | NULL | |
| created_at | DATETIME | NOT NULL | |

## 3. 业务规则

- 对象编码唯一；字段类型 TEXT/NUMBER/DATE/SELECT。
- 停用对象不可新建记录（422 OBJECT_DISABLED）；删除对象逻辑删除。
- 记录值 JSON；搜索 values LIKE。
