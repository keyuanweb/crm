# Research: 智能线索评分与销售预测校准

## R1 线索评分规则引擎

**决策**: 规则引擎计分，维度分值可配置（存 `lead_score_config` 表），总分 0-100 封顶。非 ML（YAGNI）。

**评分维度（默认）**:

| 维度 | 满分 | 计分逻辑 |
|---|---|---|
| 来源渠道 | 30 | REFERRAL=30 / WEBSITE=25 / EXHIBITION=20 / AD=15 / COLD_CALL=10 / OTHER=10 |
| 信息完整度 | 30 | company/title/phone/email 每项 7.5 分（有值即得） |
| 跟进活跃度 | 25 | 有跟进记录：最近 7 天跟进满分，每超 7 天递减；无跟进 0 分 |
| 互动时效 | 15 | 创建 ≤7 天满分，每超 7 天递减，>30 天 0 分 |

**颜色阈值**: <40 红 / 40-69 黄 / ≥70 绿（可配置）。

**触发时机**: `LeadService.create/update` 后、`FollowUpService.create`（含 leadId）后、`LeadService.claim` 后。

## R2 阶段转化率校准

**决策**: 从 `sales_opportunity` 表统计各阶段历史转化率，样本 ≥10 用历史值，否则回退默认（INITIAL_CONTACT=0.2、NEGOTIATING=0.5），CLOSED_WON/LOST 固定 1.0/0.0。

**统计口径**: 某阶段转化率 = （该阶段状态为 CLOSED_WON 或已进入下一阶段的活跃机会数）/（该阶段总机会数）。简化实现：统计每阶段处于该阶段的活跃机会数 + 该阶段赢单数，转化率 = 赢单数 / 总量（保守口径）。

**缓存**: 统计结果 Redis 缓存 5 分钟（key `stats:stage-conversion`），减少重复聚合。

## R3 前端展示

**决策**: 线索列表/线索池按 score 降序；评分 Tag 红黄绿展示（复用 LeadListPage 现有 score Tag 逻辑，改为自动评分后阈值显示）；预测接口新增 `probabilitySource` 字段，DashboardPage 预测区块可标注"历史校准/默认"。

## R4 与既有功能的关系

- Lead.score 字段（004）复用，自动评分覆盖手工值（FR-002）。
- 前端编辑弹窗的"评分（0-100）"手工输入项移除或保留为覆盖项？——保留手工覆盖能力但默认自动评分（FR-002 自动覆盖，管理员可改回手工）。
- 预测校准不影响首页漏斗（阶段数量/金额），仅影响加权预测金额。
