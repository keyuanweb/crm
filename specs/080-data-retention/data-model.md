# Data Model: 数据保留策略（Data Retention Policy）

**创建日期**: 2026-08-27

## 实体关系图

```
┌─────────────────────────────────┐       ┌─────────────────────────────────┐
│  DataRetentionPolicy            │1    *│  DataRetentionExecution         │
│─────────────────────────────────│──────│─────────────────────────────────│
│ id (PK)                         │      │ id (PK)                         │
│ entity_type                     │      │ policy_id (FK)                  │
│ retention_period_years          │      │ executed_at                     │
│ retention_period_months         │      │ status                          │
│ retention_period_days           │      │ records_processed               │
│ archive_action                  │      │ error_message                   │
│ status                          │      │ created_at                      │
│ created_by (FK)                 │      └─────────────────────────────────┘
│ created_at                      │
│ updated_at                      │
└─────────────────────────────────┘

┌─────────────────────────────────┐
│  DataArchiveLog                 │
│─────────────────────────────────│
│ id (PK)                         │
│ policy_id (FK)                  │
│ source_table                    │
│ source_record_id                │
│ archive_table                   │
│ archived_at                     │
│ archived_by (FK)                │
│ created_at                      │
└─────────────────────────────────┘
```

## 表结构详情

### DataRetentionPolicy（数据保留策略表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| entity_type | VARCHAR(50) | NOT NULL | 实体类型（CUSTOMER/OPPORTUNITY/CONTRACT/ORDER/INVOICE/FOLLOW_UP/AUDIT_LOG） |
| retention_period_years | INT | NOT NULL, DEFAULT 0 | 保留年限 |
| retention_period_months | INT | NOT NULL, DEFAULT 0 | 保留月数 |
| retention_period_days | INT | NOT NULL, DEFAULT 0 | 保留天数 |
| archive_action | VARCHAR(20) | NOT NULL, DEFAULT 'ARCHIVE' | 归档方式（ARCHIVE/DELETE） |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'ACTIVE' | 状态（ACTIVE/SUSPENDED） |
| created_by | BIGINT | FK → User.id, NOT NULL | 策略创建者 ID |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() ON UPDATE NOW() | 更新时间 |

**索引**:
- `idx_entity_type`: (entity_type)
- `idx_status`: (status)
- `idx_created_by`: (created_by)

**唯一约束**:
- `uk_entity_type`: (entity_type) - 同一实体类型只有一个活跃策略

### DataRetentionExecution（执行记录表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| policy_id | BIGINT | FK → DataRetentionPolicy.id, NOT NULL | 策略 ID |
| executed_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 执行时间 |
| status | VARCHAR(20) | NOT NULL | 状态（SUCCESS/FAILED/PARTIAL） |
| records_processed | INT | NOT NULL, DEFAULT 0 | 处理记录数 |
| error_message | TEXT | NULL | 错误信息 |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 创建时间 |

**索引**:
- `idx_policy_id`: (policy_id)
- `idx_executed_at`: (executed_at)
- `idx_status`: (status)

### DataArchiveLog（归档日志表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| policy_id | BIGINT | FK → DataRetentionPolicy.id, NOT NULL | 策略 ID |
| source_table | VARCHAR(50) | NOT NULL | 源表名 |
| source_record_id | BIGINT | NOT NULL | 源记录 ID |
| archive_table | VARCHAR(50) | NOT NULL | 归档表名 |
| archived_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 归档时间 |
| archived_by | BIGINT | FK → User.id, NOT NULL | 归档操作者 ID |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 创建时间 |

**索引**:
- `idx_policy_id`: (policy_id)
- `idx_source_table_record`: (source_table, source_record_id)
- `idx_archived_at`: (archived_at)

## 归档表示例

### customer_archive（客户归档表）

结构与 customer 表一致，额外添加：
- `archived_at` TIMESTAMP NOT NULL - 归档时间
- `archived_by` BIGINT - 归档操作者 ID

### opportunity_archive（商机归档表）

结构与 opportunity 表一致，额外添加：
- `archived_at` TIMESTAMP NOT NULL - 归档时间
- `archived_by` BIGINT - 归档操作者 ID

## 数据完整性规则

1. **策略唯一性**: 同一实体类型只有一个 ACTIVE 策略
2. **保留期限计算**: 基于实体 created_at 字段 + 保留期限（年/月/日）
3. **归档分批**: 每批最多 1000 条记录，避免锁表
4. **归档日志**: 每条归档记录创建 DataArchiveLog 日志
5. **策略变更审计**: 策略修改创建审计日志（记录变更人、变更前后值）
