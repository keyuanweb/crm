# Research: 销售仪表盘模块

**Branch**: `006-sales-dashboard` | **Date**: 2026-08-22

## 1. 聚合查询与缓存策略

**Decision**: 单接口 `/stats/dashboard` 一次返回全部聚合数据；Redis 缓存 5 分钟；业务写操作后调用 `evict()` 失效。

**Rationale**: 仪表盘为只读聚合视图，多卡片共用同一数据源；一次查询+一次缓存键避免 N 次请求与缓存碎片。与既有 `OpportunityStatsService`（管道统计）缓存模式一致。

**Alternatives considered**: 每卡片独立接口（前端可局部刷新，但请求数多、缓存键多、数据一致性差）——否决。

## 2. 销售目标（SalesTarget）模型

**Decision**: 新表 `sales_target`（Flyway V11），字段：`target_month`（VARCHAR(7)，YYYY-MM）、`target_amount`（BIGINT，分）、`created_by`、逻辑删除/乐观锁/时间戳。按月唯一（同 V6 的 active_key 生成列模式：`deleted=0` 时 `CONCAT('m||', target_month)` 参与唯一索引）。`PUT /stats/sales-targets` 为 upsert 语义：当月目标已存在则更新金额，否则插入。

**Rationale**: 目标粒度按整月（spec 假设），一表一目标；upsert 避免"重复设置"报错，符合业务直觉。active_key 模式与既有 V6 客户唯一约束一致。

**Alternatives considered**: 按人/团队拆分目标表（未来数据权限模块再做）——当前范围外，否决。

## 3. 预测概率与停滞预警阈值

**Decision**: 预测概率固定映射 `INITIAL_CONTACT→0.2、NEGOTIATING→0.5、CLOSED_WON→1.0、CLOSED_LOST→0.0`；停滞预警阈值为可配置常量（默认 7 天，`spring.crm.stalled-days` 或代码常量），作用于 `stage IN (INITIAL_CONTACT, NEGOTIATING)`（活跃未终结）且 `updated_at` 早于 `now - N days` 的销售机会，按停滞天数倒序。

**Rationale**: spec 假设明确概率固定、阈值默认 7 天可配置。活跃阶段定义采用销售管道惯例（终态不参与预警）。

**Alternatives considered**: 每机会自定义概率/阈值（管理复杂度高，后续增强）——否决。

## 4. 赢单率与达成率口径

**Decision**: 赢单率 = `CLOSED_WON 数量 / (CLOSED_WON + CLOSED_LOST) 数量`（仅已关闭机会，避免未关闭机会稀释）；业绩达成率 = `当月 CLOSED_WON 金额合计 / 当月目标金额 × 100%`（当月按 `closed_at` 落月）。

**Rationale**: 赢单率以已关闭为分母是销售漏斗标准口径；达成率用 closed_at 而非创建时间，反映实际回款月归属。

**Alternatives considered**: 赢单率分母用全部机会（未关闭机会会人为拉低，误导）——否决。

## 5. 前端渲染方案

**Decision**: 前端复用 antd `Card/Statistic/Table/Tag` 与 ProLayout；漏斗与方式分布用表格+进度条（`Progress`）呈现，不引入额外图表库（YAGNI）。

**Rationale**: 既有技术栈无图表库；仪表盘数据量小，表格+进度条清晰且零新增依赖。后续需要趋势图再评估图表库。

**Alternatives considered**: 引入 ECharts/antd-charts（视觉更佳但新增依赖与体积，且当前需求无趋势曲线）——否决。

## 6. 契约与权限

**Decision**: 仪表盘接口权限 `hasAnyRole('ADMIN','SALES')`（与既有 StatsController 一致）；目标查询同权限；目标设置 `hasRole('ADMIN')`（spec FR-D11）。契约写入 `contracts/stats.md` 扩展。

**Rationale**: 既有 stats 域权限矩阵不变；管理员专有写操作符合"角色对齐"。
