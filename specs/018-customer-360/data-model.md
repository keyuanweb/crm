# 数据模型：客户 360 与健康度评分

## 聚合视图 DTO（无新表）

### Customer360Response

| 字段 | 类型 | 说明 |
|---|---|---|
| id / name / company / ... | 继承 CustomerDetail 字段 | 基本信息 |
| contacts | List<ContactResponse> | 联系人（已有） |
| followUps | List<FollowUpBrief> | 跟进时间线（已有） |
| opportunities | List<OpportunityBrief> | 商机 + 销售机会数（已有） |
| orders | List<OrderBrief> | 订单（新增：orderNo/title/amount/status） |
| paymentSummaries | List<PaymentSummary> | 回款摘要（新增：计划/已回款/逾期） |
| contracts | List<ContractBrief> | 合同（新增：contractNo/title/amount/status） |
| tickets | List<TicketBrief> | 工单（新增：title/priority/status/slaStatus） |
| amountSummary | AmountSummary | 金额汇总（新增：totalOrder/paid/dueOverdue） |
| health | HealthScoreDTO | 健康度评分（新增） |

### HealthScoreDTO

| 字段 | 类型 | 说明 |
|---|---|---|
| score | int | 0-100 |
| level | String | RED / YELLOW / GREEN |
| deductions | List<ScoreDeduction> | 失分原因列表（维度 + 扣分） |

### CustomerHealthBrief（预警列表项）

| 字段 | 类型 | 说明 |
|---|---|---|
| id / name / company | | 客户标识 |
| healthScore | int | 健康度 |
| lastFollowUpAt | LocalDateTime? | 最近跟进时间 |
| lastOrderAt | LocalDateTime? | 最近订单时间 |
| daysInactive | int | 无活动天数 |
| ownerName | String | 负责人 |

## 评分配置表 health_score_config（新表，Flyway V42）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| dimension_key | VARCHAR(50) | FOLLOWUP / PAYMENT / TICKET / DEPTH / ACTIVITY |
| dimension_label | VARCHAR(100) | 中文名（跟进活跃度等） |
| weight | INT | 权重（合计 100） |
| params_json | VARCHAR(500) | 参数（如天数阈值，JSON） |
| enabled | TINYINT | 是否启用 |
| sort_order | INT | 排序 |

**默认种子**（5 行，对应 research.md R2 五维度）。

## 约束

- 评分不落库，实时计算（`Customer360Response.health` 随查询返回）。
- 金额单位为分（与 SalesOrder.amount 一致），前端 formatAmount 展示。
- 所有聚合只读，无写路径。
