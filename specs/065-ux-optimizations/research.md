# Research: 体验优化批次

**Branch**: `065-ux-optimizations` | **Date**: 2026-08-26

## 1. 销售预测去重

**Decision**: 后端 `computeForecast` 按阶段聚合（同阶段 amount/weighted 求和，probability/source 阶段级一致）；前端 `aggregateForecast` 渲染前按 stage 兜底聚合。双保险。

**Rationale**: 原实现逐商机生成 breakdown 导致同阶段多条；聚合后语义正确（预测按阶段汇总）。

## 2. 详情页/产品页

**Decision**: 详情页就地编辑（updateCustomer + invalidateQueries 即时刷新）+ 头部可点击电话/邮箱 + 健康度徽章 + Descriptions 响应式。产品编辑集成 057 多币种价（openEdit 加载、保存时差集同步 setProductPrice/deleteProductPrice）。

**Rationale**: 复用既有 API 与缓存机制；多币种填补产品编辑与 057 的集成断层。

## 3. 整体布局

**Decision**: page-container 限宽 1680px 居中（防大屏行过长）、page-scroll 滚动条美化、卡片间距统一 16px、桌面侧栏折叠开关、NotFoundPage 双层 404、路由切换滚动复位 + page-fade 淡入。

**Rationale**: 纯展示层；限宽经用户反馈从 1440 调至 1680、padding 20→12（避免过度缩进）。

## 4. 文案/显示修正

**Decision**: i18n 插值单花括号 {name} → 双花括号 {{name}}（i18next 默认前缀）；客户列表去掉脱敏（用户要求显示完整，导出仍按 063 对非管理员脱敏）。

**Rationale**: i18next 语法修正；列表完整显示为明确需求，安全权衡已确认。
