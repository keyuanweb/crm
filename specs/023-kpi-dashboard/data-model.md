# Data Model: 大屏数据看板（KPI 大屏）



## KpiBoardResponse（派生聚合，Redis 缓存 5 分钟）



| 字段 | 类型 | 说明 |

|---|---|---|

| kpi | DashboardStats.Summary | 核心指标（商机/金额/赢单率/客户数/本月新增） |

| funnel | DashboardStats.Funnel | 销售漏斗 |

| leaderboard | List<LeaderboardItem> | 团队排行 TopN（默认 10） |

| healthDistribution | HealthDistribution | 健康度分布（red/yellow/green） |

| suggestions | SuggestionSummary | 智能建议摘要 |

| trend | List<TrendPoint> | 近 30 天趋势（date/count/amount） |



### HealthDistribution



| 字段 | 类型 | 说明 |

|---|---|---|

| red / yellow / green | int | 各健康度等级客户数 |



### TrendPoint



| 字段 | 类型 | 说明 |

|---|---|---|

| date | String | yyyy-MM-dd |

| count | long | 当日商机数量 |

| amount | long | 当日商机金额 |



## 约束



- 无新表；聚合结果 Redis 缓存（`stats:kpi-board`，TTL 5 分钟）。

- 健康度分布抽查最多 200 客户。

- 大屏仅 ADMIN。

