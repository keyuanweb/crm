# Data Model: 智能线索评分与销售预测校准

## 评分规则表 lead_score_config（新表，Flyway V43）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| rule_key | VARCHAR(50) | SOURCE / INFO / FOLLOWUP / FRESHNESS |
| rule_label | VARCHAR(100) | 中文名 |
| params_json | VARCHAR(500) | 分值映射 JSON（如来源分值表） |
| enabled | TINYINT | 是否启用 |
| sort_order | INT | 排序 |
| deleted / version / created_at / updated_at | | 通用字段 |

**默认种子**（4 行，对应 research.md R1 四维度）。

## 线索评分（复用 lead.score 字段）

- 计算后写回 `lead.score`（0-100），无新表。
- 颜色阈值（40/70）作为配置项存 `lead_score_config`（rule_key=THRESHOLD）或应用配置。

## 阶段转化率（派生，不落库）

| 字段 | 类型 | 说明 |
|---|---|---|
| stage | String | INITIAL_CONTACT / NEGOTIATING |
| probability | double | 转化率（历史或默认） |
| sampleSize | int | 历史样本数 |
| source | String | HISTORICAL / DEFAULT |

## ForecastItem 扩展

| 字段 | 类型 | 说明 |
|---|---|---|
| probabilitySource | String | 概率来源（HISTORICAL / DEFAULT），前端展示用 |

## 约束

- 评分规则变更即时生效（实时读配置表，不缓存或短缓存）。
- 转化率统计只读，5 分钟 Redis 缓存。
