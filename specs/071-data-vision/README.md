# 酷炫数据大屏（Data Vision）

## 概述

酷炫数据大屏是一个全屏展示的可视化仪表盘，用于实时监控 CRM 核心业务指标。采用深色主题设计，包含粒子动画背景、流光边框、霓虹文字、数字滚动动画和 ECharts 图表动画。

## 技术栈

- **前端框架**: React 18.2.0 + TypeScript
- **UI 组件库**: Ant Design 5.22.0
- **图表库**: ECharts 5.x + echarts-for-react
- **动画**: Canvas API + CSS Animation + requestAnimationFrame
- **全屏**: Fullscreen API（支持 webkit 前缀降级）
- **响应式**: CSS Grid + Media Queries

## 功能特性

### 核心功能

| 功能 | 描述 | 状态 |
|------|------|------|
| FR-DV01 | 粒子动画背景 | ✅ |
| FR-DV02 | 6 个核心 KPI 指标卡 | ✅ |
| FR-DV03 | 销售漏斗图（ECharts 横向条形图） | ✅ |
| FR-DV04 | 团队排行 Top10 | ✅ |
| FR-DV05 | 客户健康度分布（ECharts 环形图） | ✅ |
| FR-DV06 | 商机金额趋势（ECharts 折线图/面积图） | ✅ |
| FR-DV07 | 智能建议摘要 | ✅ |
| FR-DV08 | 全屏展示（进入/退出全屏） | ✅ |
| FR-DV09 | 响应式布局（3/4/5 列切换） | ✅ |
| FR-DV10 | 自动刷新（30 秒间隔） | ✅ |
| FR-DV11 | 手动刷新按钮 | ✅ |
| FR-DV12 | 数字滚动动画 | ✅ |
| FR-DV13 | 流光边框效果 | ✅ |
| FR-DV14 | 霓虹文字效果 | ✅ |
| FR-DV15 | 深色主题视觉设计 | ✅ |
| FR-DV16 | 鼠标悬停 tooltip 效果 | ✅ |

### 用户故事

| 用户故事 | 描述 | 状态 |
|----------|------|------|
| US1 | 作为管理员，我想全屏查看数据大屏，以便在会议室展示 | ✅ |
| US2 | 作为销售总监，我想实时查看核心 KPI 指标，以便掌握业务动态 | ✅ |
| US3 | 作为运营经理，我想查看销售漏斗和团队排行，以便优化销售策略 | ✅ |
| US4 | 作为 CEO，我想查看客户健康度和智能建议，以便做出战略决策 | ✅ |

## 文件结构

```
frontend/src/pages/dataVision/
├── DataVisionPage.tsx          # 主页面组件
├── types.ts                    # 类型定义
├── hooks/
│   ├── useCountUp.ts           # 数字滚动动画 Hook
│   ├── useFullscreen.ts        # 全屏 API Hook
│   └── useResponsiveGrid.ts    # 响应式布局 Hook
├── components/
│   ├── ParticleBackground.tsx  # 粒子动画背景
│   ├── CountUp.tsx             # 数字滚动动画组件
│   ├── GlowBorder.tsx          # 流光边框组件
│   ├── NeonText.tsx            # 霓虹文字组件
│   ├── KpiMetricCard.tsx       # KPI 指标卡
│   ├── KpiMetricsRow.tsx       # KPI 指标卡行
│   ├── FunnelChart.tsx         # 销售漏斗图
│   ├── LeaderboardChart.tsx    # 团队排行
│   ├── HealthChart.tsx         # 客户健康度分布
│   ├── TrendChart.tsx          # 商机金额趋势
│   └── SuggestionCards.tsx     # 智能建议摘要
└── styles/
    └── dataVision.css          # 基础样式
```

## 视觉设计

### 配色方案

- **背景色**: `#0a1830`（深蓝）
- **主色调**: `#4da3ff`（亮蓝）
- **辅助色**: `#52c41a`（绿）、`#faad14`（黄）、`#ff4d4f`（红）
- **文字色**: `#7db4ff`（浅蓝）、`#ffffff`（白）

### 动画效果

1. **粒子动画**: Canvas API 绘制，粒子间连线效果，帧率不低于 30 FPS
2. **数字滚动**: requestAnimationFrame + easeOutExpo 缓动函数
3. **流光边框**: CSS Animation 实现渐变边框效果
4. **图表动画**: ECharts 内置动画，渐变填充，动态绘制

## 性能优化

1. **粒子动画**: 使用 requestAnimationFrame，限制粒子数量（100 个）
2. **ECharts 图表**: setOption notMerge，dispose，resize 优化
3. **数字滚动**: requestAnimationFrame + transform 优化
4. **响应式布局**: CSS Grid + Media Queries，根据屏幕分辨率自动调整列数

## 兼容性

- Chrome 90+
- Edge 90+
- Firefox 88+
- Safari 14+

## 使用方式

1. 访问 `/data-vision` 路由
2. 点击全屏按钮或按 F11 进入全屏模式
3. 按 ESC 键退出全屏
4. 每 30 秒自动刷新数据，也可点击手动刷新按钮

## 数据来源

完全复用现有 KPI 大屏 API：`GET /api/v1/kpi-board`

## 测试

```bash
# 运行单元测试
npx vitest run src/pages/dataVision/

# 运行 TypeScript 编译检查
npx tsc --noEmit

# 构建验证
npx vite build
```
