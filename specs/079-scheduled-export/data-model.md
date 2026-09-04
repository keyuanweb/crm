# Data Model: 定时导出订阅（Scheduled Export Subscription）

**创建日期**: 2026-08-27

## 实体关系图

```
┌─────────────────────────┐       ┌─────────────────────────────────┐
│  ScheduledExport        │1    *│  ScheduledExportExecution       │
│─────────────────────────│──────│─────────────────────────────────│
│ id (PK)                 │      │ id (PK)                         │
│ created_by (FK)         │      │ export_id (FK)                  │
│ entity_type             │      │ executed_at                     │
│ filter_conditions (JSON)│      │ status                          │
│ export_format           │      │ file_path                       │
│ execution_cron          │      │ file_size_bytes                 │
│ next_execution_time     │      │ email_sent                      │
│ status                  │      │ error_message                   │
│ created_at              │      │ created_at                      │
│ updated_at              │      └─────────────────────────────────┘
└─────────────────────────┘
```

## 表结构详情

### ScheduledExport（定时导出任务表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| created_by | BIGINT | FK → User.id, NOT NULL | 任务创建者 ID |
| entity_type | VARCHAR(50) | NOT NULL | 实体类型（CUSTOMER/OPPORTUNITY/CONTRACT/ORDER/INVOICE） |
| filter_conditions | JSON | NOT NULL | 筛选条件（JSON 格式） |
| export_format | VARCHAR(10) | NOT NULL, DEFAULT 'CSV' | 导出格式（CSV/XLSX） |
| execution_cron | VARCHAR(50) | NOT NULL | Cron 表达式（如 "0 0 9 * * MON"） |
| next_execution_time | TIMESTAMP | NOT NULL | 下次执行时间 |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'ACTIVE' | 状态（ACTIVE/SUSPENDED/DELETED） |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() ON UPDATE NOW() | 更新时间 |

**索引**:
- `idx_created_by_status`: (created_by, status)
- `idx_next_execution`: (next_execution_time)
- `idx_status`: (status)

**唯一约束**:
- `uk_created_by_active`: (created_by, status) - 同一用户最多 10 个 ACTIVE 任务（通过应用层校验，非数据库约束）

### ScheduledExportExecution（执行记录表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| export_id | BIGINT | FK → ScheduledExport.id, NOT NULL | 定时任务 ID |
| executed_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 执行时间 |
| status | VARCHAR(20) | NOT NULL | 状态（SUCCESS/FAILED/EMAIL_SENT/EMAIL_FAILED） |
| file_path | VARCHAR(500) | NULL | 导出文件路径 |
| file_size_bytes | BIGINT | NULL | 文件大小（字节） |
| email_sent | BOOLEAN | NOT NULL, DEFAULT FALSE | 邮件是否发送成功 |
| email_sent_at | TIMESTAMP | NULL | 邮件发送时间 |
| error_message | TEXT | NULL | 错误信息 |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 创建时间 |

**索引**:
- `idx_export_id`: (export_id)
- `idx_executed_at`: (executed_at)
- `idx_status`: (status)

## 状态流转

### ScheduledExport 状态

```
ACTIVE ↔ SUSPENDED
  │
  └──→ DELETED
```

- **ACTIVE**: 活跃状态，定时任务正常执行
- **SUSPENDED**: 已暂停，跳过下次执行，不删除任务
- **DELETED**: 已删除，不再执行，保留执行历史

### ScheduledExportExecution 状态

```
SUCCESS → EMAIL_SENT
  │          │
  └──→ EMAIL_FAILED  ──→ (重试) → EMAIL_SENT
FAILED (导出失败，不发送邮件)
```

- **SUCCESS**: 导出成功
- **FAILED**: 导出失败（查询超时、数据量过大等）
- **EMAIL_SENT**: 邮件发送成功
- **EMAIL_FAILED**: 邮件发送失败（重试 3 次后仍失败）

## 数据完整性规则

1. **任务数量限制**: 同一用户最多 10 个 ACTIVE 任务（应用层校验）
2. **数据量限制**: 单次导出最多 10 万行（执行前预估，超出则拒绝）
3. **筛选条件校验**: 创建任务时校验筛选条件合法性（实体字段是否存在）
4. **Cron 表达式校验**: 创建任务时校验 Cron 表达式合法性
5. **文件清理**: 导出文件邮件发送后删除，过期文件（>7 天）定时清理
