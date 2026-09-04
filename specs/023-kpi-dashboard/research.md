# Research: 大屏数据看板（KPI 大屏）



## R1 聚合接口组合



**决策**: `KpiBoardService` 注入既有服务组合输出，不重复实现：

- KPI 指标 + 漏斗：`DashboardStatsService.getDashboard()`（006）

- 团队排行 TopN：`TeamLeaderboardService.leaderboard(month)`（020）

- 建议摘要：`SuggestionService.summary()`（022）

- 健康度分布：遍历客户（最多 200）调 `Customer360Service.aggregate(id).getHealth().getLevel()` 统计红/黄/绿

- 30 天趋势：`SalesOpportunityMapper` 按 created_at 分组（近 30 天，按日数量/金额）



## R2 健康度分布性能



**决策**: 健康度分布遍历最多 200 个客户（`CustomerService.page` 取可见客户），逐个调 aggregate 成本高——改为复用 Customer360Service 的健康度但限制数量（200），或直接复用 at-risk 逻辑。为控制成本，健康度分布按"抽查最多 200 客户"计算（Assumptions 已声明），Redis 缓存 5 分钟兜底。



## R3 前端大屏



**决策**: `KpiBoardPage` 深色主题（#0f1e3d 底 + 蓝紫渐变卡），CSS Grid 布局：

- 顶部：标题 + 时间 + 刷新状态

- 中部：KPI 卡行（商机数/金额/赢单率/客户数/本月新增/建议数）

- 下部：漏斗（横向条）、排行 TopN（表格）、健康度分布（三色计数卡）、30 天趋势（SVG 折线）

自动刷新：`setInterval` 60s 重新 fetch；失败保留旧数据 + 提示。全屏：`document.documentElement.requestFullscreen()`。



## R4 图表自绘



**决策**: 30 天趋势用原生 SVG polyline（按日金额映射），不引入 echarts（YAGNI）；漏斗用 div 宽度百分比条（复用首页漏斗风格）。

