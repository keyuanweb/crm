# 数据模型：智能建议

## 建议忽略记录（Redis，无新表）

| 字段 | 说明 |
|---|---|
| key | `ai:ignore:<userId>:<type>:<entityId>` |
| value | "1" |
| TTL | 90 天 |

## SmartSuggestion（派生）

| 字段 | 类型 | 说明 |
|---|---|---|
| type | String | CUSTOMER_AT_RISK / OPPORTUNITY_STALLED / CUSTOMER_FOLLOWUP / LEAD_HIGH_SCORE |
| title | String | 建议标题（如"跟进客户：Acme 科技"） |
| reason | String | 原因（如"已 53 天未跟进，健康度 35"） |
| priority | String | URGENT / IMPORTANT / NORMAL |
| entityType | String | CUSTOMER / OPPORTUNITY / LEAD |
| entityId | Long | 关联实体 id |
| action | String | follow_up / push / process |

## SuggestionSummary（首页摘要）

| 字段 | 类型 | 说明 |
|---|---|---|
| atRiskCustomers | int | 流失预警数 |
| stalledOpportunities | int | 停滞商机数 |
| followUpCustomers | int | 待跟进客户数 |
| highScoreLeads | int | 高分线索数 |

## 约束

- 建议实时计算，无持久化。
- 忽略仅当前用户生效。
