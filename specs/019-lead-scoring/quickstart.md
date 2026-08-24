# 快速开始：智能线索评分与销售预测校准

## 后端

1. Flyway `V43__lead_score_config.sql`：建 `lead_score_config` 表 + 默认种子（来源 30 / 信息完整度 30 / 跟进活跃度 25 / 互动时效 15）。
2. `LeadScoreConfig` 实体 + Mapper。
3. `LeadScoreService`：读配置 → 按四维度计算 0-100 分。
4. `LeadService.create/update` + `FollowUpService.create`（含 leadId）+ `LeadService.claim` 后调用评分写回。
5. `StageConversionService`：从 sales_opportunity 统计各阶段转化率（样本 ≥10 用历史，否则回退默认）。
6. `DashboardStatsService.computeForecast` 改用校准概率，ForecastItem 加 `probabilitySource`。
7. 测试：`LeadScoreServiceTest` + `StageConversionServiceTest` + `LeadScoringIT`。

## 前端

1. 线索列表/线索池按评分降序（`sortBy=score`），评分 Tag 红黄绿展示。
2. 预测区块展示 `probabilitySource` 标注（历史校准/默认）。
3. 线索编辑弹窗保留手工评分覆盖能力（可选）。

## 验证

- 后端：`mvn test`（新增测试，不影响既有 208）。
- 前端：`pnpm run typecheck` + `lint` + `test`。
- 手动：创建线索见自动评分；首页预测见校准概率与来源标注。
