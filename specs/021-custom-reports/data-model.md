# Data Model: 自定义报表

## 报表模板表 report_template（新表，Flyway V45，P3）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| name | VARCHAR(100) | 模板名 |
| dimension | VARCHAR(30) | SALES/PRODUCT/SOURCE/STAGE/TIME |
| metric | VARCHAR(20) | COUNT/AMOUNT |
| granularity | VARCHAR(10) | DAY/MONTH（时间维度时） |
| start_date / end_date | DATE | 时间范围 |
| created_by | BIGINT | 创建人（ADMIN） |
| deleted / version / created_at / updated_at | | 通用字段 |

## 报表结果（派生，无新表）

### ReportRow

| 字段 | 类型 | 说明 |
|---|---|---|
| dimensionValue | String | 维度值（销售名/产品名/来源/阶段/日期） |
| count | long | 数量 |
| amount | long | 金额（分） |
| ratio | double | 金额占比（0-1） |

### ReportResult

| 字段 | 类型 | 说明 |
|---|---|---|
| rows | List<ReportRow> | 聚合行（按金额降序） |
| totalCount / totalAmount | long | 合计 |
| dimension / metric / granularity | | 回显请求 |

## 约束

- 聚合实时计算，无缓存无持久化。
- 金额单位为分。
