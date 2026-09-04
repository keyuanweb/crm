# Data Model: 销售配额分解（Sales Quota Decomposition）

**创建日期**: 2026-08-27

## 实体关系图

```
┌─────────────────┐       ┌─────────────────────────┐       ┌─────────────────┐
│  SalesQuota     │1    *│  SalesQuotaBreakdown    │ *    1│  SalesQuota     │
│─────────────────│──────│─────────────────────────│──────│─────────────────│
│ id (PK)         │      │ id (PK)                 │      │ id (PK)         │
│ parent_id (FK)  │      │ parent_quota_id (FK)    │      │ year          │
│ quarter         │      │ child_quota_id (FK)     │      │ quarter       │
│ year            │      │ amount                  │      │ team_id (FK)    │
│ team_id (FK)    │      │ created_at              │      │ user_id (FK)    │
│ user_id (FK)    │      │ updated_at              │      │ amount          │
│ amount          │      └─────────────────────────┘      │ status          │
│ status          │                                       │ period_start    │
│ period_start    │                                       │ period_end      │
│ period_end      │                                       │ created_at      │
│ created_at      │                                       │ updated_at      │
│ updated_at      │                                       └─────────────────┘
│ closed (boolean)│
└─────────────────┘

┌─────────────────────────┐       ┌─────────────────────────┐
│  SalesQuotaVersion      │       │  SalesQuotaAchievement  │
│─────────────────────────│       │─────────────────────────│
│ id (PK)                 │       │ id (PK)                 │
│ quota_id (FK)           │       │ quota_id (FK)           │
│ old_amount              │       │ actual_amount           │
│ new_amount              │       │ achievement_rate        │
│ changed_by (FK)         │       │ calculated_at           │
│ changed_at              │       │ quota_year              │
│ change_reason           │       │ quota_quarter           │
│ version_number          │       │ quota_team_id (FK)      │
│ created_at              │       │ quota_user_id (FK)      │
└─────────────────────────┘       └─────────────────────────┘
```

## 表结构详情

### SalesQuota（销售配额主表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| parent_id | BIGINT | FK → SalesQuota.id, NULL | 上级配额 ID（年度配额为 NULL） |
| quarter | SMALLINT | NULL | 季度（1-4），年度配额为 NULL |
| year | INT | NOT NULL | 年份 |
| team_id | BIGINT | FK → Department.id, NULL | 团队 ID（个人配额为 NULL） |
| user_id | BIGINT | FK → User.id, NULL | 用户 ID（团队配额为 NULL） |
| amount | DECIMAL(15,2) | NOT NULL | 配额金额（万元） |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'ACTIVE' | 状态：ACTIVE/DRAFT/CLOSED |
| period_start | DATE | NOT NULL | 期间开始日期 |
| period_end | DATE | NOT NULL | 期间结束日期 |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() ON UPDATE NOW() | 更新时间 |

**索引**:
- `idx_year_team`: (year, team_id)
- `idx_year_user`: (year, user_id)
- `idx_parent_id`: (parent_id)
- `idx_status`: (status)

**唯一约束**:
- `uk_year_team_user`: (year, team_id, user_id) - 同一用户同一期间只有一个配额

### SalesQuotaVersion（配额版本历史表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| quota_id | BIGINT | FK → SalesQuota.id, NOT NULL | 配额 ID |
| old_amount | DECIMAL(15,2) | NOT NULL | 调整前金额 |
| new_amount | DECIMAL(15,2) | NOT NULL | 调整后金额 |
| changed_by | BIGINT | FK → User.id, NOT NULL | 调整人 ID |
| changed_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 调整时间 |
| change_reason | VARCHAR(500) | NULL | 调整原因 |
| version_number | INT | NOT NULL | 版本号（从 1 开始递增） |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 创建时间 |

**索引**:
- `idx_quota_id`: (quota_id)
- `idx_quota_version`: (quota_id, version_number)

### SalesQuotaBreakdown（配额分解关系表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| parent_quota_id | BIGINT | FK → SalesQuota.id, NOT NULL | 上级配额 ID |
| child_quota_id | BIGINT | FK → SalesQuota.id, NOT NULL | 下级配额 ID |
| amount | DECIMAL(15,2) | NOT NULL | 分解金额 |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() ON UPDATE NOW() | 更新时间 |

**索引**:
- `idx_parent_quota`: (parent_quota_id)
- `idx_child_quota`: (child_quota_id)

**唯一约束**:
- `uk_parent_child`: (parent_quota_id, child_quota_id) - 同一父子关系唯一

**校验规则**:
- 下级配额 amount 总和必须等于上级配额 amount（误差 ≤ 0.01）

### SalesQuotaAchievement（配额达成统计表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| quota_id | BIGINT | FK → SalesQuota.id, NOT NULL | 配额 ID |
| actual_amount | DECIMAL(15,2) | NOT NULL, DEFAULT 0 | 实际销售额（万元） |
| achievement_rate | DECIMAL(5,2) | NOT NULL, DEFAULT 0 | 达成率（%） |
| calculated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | 计算时间 |
| quota_year | INT | NOT NULL | 配额年份（冗余字段，便于查询） |
| quota_quarter | SMALLINT | NULL | 配额季度（冗余字段，便于查询） |
| quota_team_id | BIGINT | FK → Department.id, NULL | 配额团队 ID（冗余字段，便于查询） |
| quota_user_id | BIGINT | FK → User.id, NULL | 配额用户 ID（冗余字段，便于查询） |

**索引**:
- `idx_quota_id`: (quota_id)
- `idx_year_quarter_team`: (quota_year, quota_quarter, quota_team_id)
- `idx_year_user`: (quota_year, quota_user_id)

## 状态流转

### SalesQuota 状态

```
DRAFT → ACTIVE → CLOSED
  ↑          │
  └──────────┘
```

- **DRAFT**: 草稿状态，允许编辑/删除
- **ACTIVE**: 激活状态，允许调整（保留版本）、查看达成率
- **CLOSED**: 已关闭状态，禁止调整、锁定历史数据

### 配额分解层级

```
年度配额（parent_id = NULL）
  ├── 季度 1
  │     ├── 团队 A
  │     │     ├── 销售 1
  │     │     └── 销售 2
  │     └── 团队 B
  │           ├── 销售 3
  │           └── 销售 4
  ├── 季度 2
  ├── 季度 3
  └── 季度 4
```

## 数据完整性规则

1. **分解总和校验**: 下级配额 amount 总和必须等于上级配额 amount（误差 ≤ 0.01）
2. **期间不重叠**: 同一团队/用户的同一期间只有一个 ACTIVE 配额
3. **时间锁**: CLOSED 状态的配额禁止调整
4. **版本保留**: 每次调整创建新版本记录，保留最近 5 个版本
5. **达成率计算**: 实际销售额从 Opportunity 表聚合（close_date 在配额期间内）
