# 数据模型：多币种模块

**Branch**: `057-multi-currency` | **Date**: 2026-08-25

## 1. currency_rate（币种汇率，Flyway V65）

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | |
| code | VARCHAR(10) | NOT NULL, UNIQUE | 币种代码（USD/EUR...） |
| name | VARCHAR(50) | NOT NULL | 币种名称 |
| rate | DECIMAL(18,6) | NOT NULL | 对基准币种汇率 |
| is_base | TINYINT | NOT NULL DEFAULT 0 | 是否基准（CNY，恒 1） |
| enabled | TINYINT | NOT NULL DEFAULT 1 | 启用 |
| created_by / created_at / updated_at | | | |

## 2. product_price（产品多币种价格）

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | |
| product_id | BIGINT | NOT NULL | 产品 |
| currency_code | VARCHAR(10) | NOT NULL | 币种 |
| price | BIGINT | NOT NULL | 价格（分） |
| created_at / updated_at | | | |

唯一约束：`uk_product_currency`（product_id, currency_code）。

## 3. 业务规则

- 基准 CNY 恒 rate=1，不可修改/删除。
- 折算：目标金额 = 基准金额 × rate（分四舍五入）。
- 产品价查询：配置价优先，未配置按汇率折算。
- 金额存储不迁移（v1 折算仅展示）。
