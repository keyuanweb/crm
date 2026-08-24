# 研究：智能建议设计

## R1 建议规则输入

| 类型 | 规则 | 数据源（复用） |
|---|---|---|
| CUSTOMER_AT_RISK | 超过 45 天无跟进且无新订单 | CustomerService.atRiskCustomers（018） |
| OPPORTUNITY_STALLED | 超过 7 天未更新的活跃机会 | DashboardStatsService.computeStalled（006） |
| CUSTOMER_FOLLOWUP | 最近跟进距今 15-45 天（待联系） | FollowUpMapper 最近跟进时间 |
| LEAD_HIGH_SCORE | 评分 ≥70 且未跟进/未转化 | LeadScoreService + LeadMapper |

## R2 优先级与排序

**决策**: 类型优先级 CUSTOMER_AT_RISK(紧急) > OPPORTUNITY_STALLED(紧急) > CUSTOMER_FOLLOWUP(重要) > LEAD_HIGH_SCORE(普通)。同类型内：流失按健康度升序、停滞按停滞天数降序、跟进按距今天数降序、线索按评分降序。整体按优先级 + 内部排序。

## R3 去重

**决策**: 同一客户同时命中流失+待跟进 → 合并为一条（取最高优先级 CUSTOMER_AT_RISK）。已跟进客户（有跟进记录且近 7 天）不产生"待跟进"建议。去重以 entityId 为键，内存 Set 判断。

## R4 忽略

**决策**: `POST /suggestions/{type}/{entityId}/ignore` → Redis SET key `ai:ignore:<userId>:<type>:<entityId>`（TTL 90 天）。生成建议时过滤命中忽略集合的实体。忽略仅当前用户可见。

## R5 数据权限

**决策**: 建议仅含当前用户可访问实体——SALES 仅本人创建/归属的客户/机会/线索，ADMIN 全量。复用各服务的权限过滤（018/019 已内置）。

## R6 前端

**决策**: `SuggestionCenterPage` 用 antd List + Tag（类型/优先级）+ 跳转链接；首页 DashboardPage 加"AI 智能建议"摘要卡（各类型计数，Statistic + 点击跳转）。
