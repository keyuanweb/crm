# Research: 前端体验优化模块

**Branch**: `039-dashboard-ux` | **Date**: 2026-08-24

## 1. 菜单切换闪烁根因与方案

**Decision**: 三层组合方案：①登录后立即（不等 requestIdleCallback）预加载全部懒加载 chunk（`import()` 幂等，首次下载缓存、后续立即 resolve）；②菜单 `onClick` 用 React `startTransition` 包裹导航，Suspense 挂起期间 React 18 保持旧 UI 而非显示 fallback；③fallback 改为与内容区同高的 `PageSkeleton`（灰条骨架）兜底。

**Rationale**: 闪烁来自懒加载 chunk 首次加载时 Suspense 渲染居中 Spin（内容区从旧页→空白/转圈→新页）。预加载让"首次"发生在登录后空闲期；startTransition 消除挂起闪退；骨架兜底保证极端情况（如预加载未完成）也不跳布局。

**Alternatives considered**: requestIdleCallback 预加载——浏览器忙时不回调导致点击仍闪，弃用；全量静态导入——首包膨胀（地图页 chunk 1.4MB），弃用；仅骨架 fallback——仍会从旧页跳骨架再跳新页，配合预加载才彻底。

## 2. 客户分析卡布局错乱根因与方案

**Decision**: ①卡片取消 `flex: 1`（父列 `display:flex` 会将其拉伸、内容少时顶部大块留白），改自然高度；②卡内统计项 `Col span={8}` 改 `xs={24} sm={8}` 响应式（窄屏垂直堆叠不挤压）；③公告卡 `height:'100%'` 在 flex 列中不生效，改 body `flex:1 + overflow:auto` 弹性填充。

**Rationale**: 错乱 = flex 拉伸留白 + 固定三列窄屏挤压；响应式栅格是 antd 标准做法。

**Alternatives considered**: 只改 span 为响应式——拉伸留白仍在；只取消 flex——窄屏仍挤压。组合修复两者。
