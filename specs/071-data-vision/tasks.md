# 实施任务：酷炫数据大屏（Data Vision）

**功能分支**: `071-data-vision`

**创建日期**: 2026-08-29

## Phase 1: 基础设施（预计 2 小时）

- [x] T001 安装 ECharts 5.x 依赖（`pnpm add echarts echarts-for-react`）
- [x] T002 创建基础目录结构（`frontend/src/pages/dataVision/`）
- [x] T003 创建 `frontend/src/pages/dataVision/types.ts` - 类型定义
- [x] T004 创建 `frontend/src/pages/dataVision/hooks/useCountUp.ts` - 数字滚动动画 Hook
- [x] T005 创建 `frontend/src/pages/dataVision/hooks/useFullscreen.ts` - 全屏 API Hook
- [x] T006 创建 `frontend/src/pages/dataVision/hooks/useResponsiveGrid.ts` - 响应式布局 Hook
- [x] T007 创建 `frontend/src/pages/dataVision/components/ParticleBackground.tsx` - 粒子动画背景组件
- [x] T008 创建 `frontend/src/pages/dataVision/components/CountUp.tsx` - 数字滚动动画组件
- [x] T009 创建 `frontend/src/pages/dataVision/components/GlowBorder.tsx` - 流光边框组件
- [x] T010 创建 `frontend/src/pages/dataVision/components/NeonText.tsx` - 霓虹文字组件
- [x] T011 创建 `frontend/src/pages/dataVision/styles/dataVision.css` - 基础样式文件
- [x] T012 验证基础组件可以渲染

## Phase 2: 核心 KPI 指标卡（预计 3 小时）

- [x] T013 创建 `frontend/src/pages/dataVision/components/KpiMetricCard.tsx` - KPI 指标卡组件
- [x] T014 实现数字滚动动画（使用 useCountUp Hook）
- [x] T015 实现流光边框效果（使用 GlowBorder 组件）
- [x] T016 创建 `frontend/src/pages/dataVision/components/KpiMetricsRow.tsx` - KPI 指标卡行组件
- [x] T017 集成现有 KPI 数据（复用 kpiBoardService）
- [x] T018 实现数据刷新时数值平滑过渡动画
- [x] T019 验证 6 个指标卡数值正确，动画流畅

## Phase 3: 动态图表可视化（预计 6 小时）

- [x] T020 创建 `frontend/src/pages/dataVision/components/FunnelChart.tsx` - 销售漏斗图（横向条形图）
- [x] T021 实现 ECharts 横向条形图，带渐变填充和流光边框
- [x] T022 创建 `frontend/src/pages/dataVision/components/LeaderboardChart.tsx` - 团队排行 Top10
- [x] T023 实现团队排行列表，带排名徽章和进度条
- [x] T024 创建 `frontend/src/pages/dataVision/components/HealthChart.tsx` - 客户健康度分布（环形图）
- [x] T025 实现 ECharts 环形图，带脉冲动画
- [x] T026 创建 `frontend/src/pages/dataVision/components/TrendChart.tsx` - 近 30 天商机金额趋势（折线图/面积图）
- [x] T027 实现 ECharts 折线图/面积图，带渐变填充和动态绘制动画
- [x] T028 创建 `frontend/src/pages/dataVision/components/SuggestionCards.tsx` - 智能建议摘要
- [x] T029 实现智能建议卡片，带图标和颜色编码
- [x] T030 集成所有图表，验证动画流畅（帧率不低于 30 FPS）
- [x] T031 实现鼠标悬停 tooltip 效果

## Phase 4: 全屏与响应式布局（预计 3 小时）

- [x] T032 实现 Fullscreen API 集成（使用 useFullscreen Hook）
- [x] T033 实现全屏切换按钮 UI
- [x] T034 实现 ESC 键退出全屏支持
- [x] T035 实现响应式布局（CSS Grid + Media Queries）
- [x] T036 实现 3/4/5 列布局切换（根据屏幕分辨率）
- [x] T037 实现字体和图表尺寸自适应
- [x] T038 验证全屏模式下布局自适应
- [x] T039 验证不同分辨率下布局自动调整

## Phase 5: 页面集成与路由（预计 2 小时）

- [x] T040 创建 `frontend/src/pages/dataVision/DataVisionPage.tsx` - 主页面组件
- [x] T041 集成所有组件（ParticleBackground, KpiMetricsRow, FunnelChart, LeaderboardChart, HealthChart, TrendChart, SuggestionCards）
- [x] T042 集成数据刷新逻辑（自动刷新 30 秒 + 手动刷新按钮）
- [x] T043 添加路由配置（`/data-vision`）
- [x] T044 添加菜单项（导航栏）
- [x] T045 集成错误处理和加载状态
- [x] T046 验证页面可以访问，数据正确展示

## Phase 6: 性能优化与测试（预计 3 小时）

- [x] T047 优化粒子动画性能（requestAnimationFrame，限制粒子数量）
- [x] T048 优化 ECharts 图表性能（setOption notMerge，dispose，resize）
- [x] T049 优化数字滚动动画性能（requestAnimationFrame，transform）
- [x] T050 使用 React.lazy() 和 Suspense 懒加载 ECharts 组件
- [x] T051 编写 `frontend/src/pages/dataVision/components/ParticleBackground.test.tsx` - 单元测试
- [x] T052 编写 `frontend/src/pages/dataVision/components/CountUp.test.tsx` - 单元测试
- [x] T053 编写 `frontend/src/pages/dataVision/components/KpiMetricCard.test.tsx` - 单元测试
- [x] T054 编写 `frontend/src/pages/dataVision/components/FunnelChart.test.tsx` - 单元测试
- [x] T055 编写 `frontend/src/pages/dataVision/components/HealthChart.test.tsx` - 单元测试
- [x] T056 编写 `frontend/src/pages/dataVision/components/TrendChart.test.tsx` - 单元测试
- [x] T057 编写 `frontend/src/pages/dataVision/DataVisionPage.test.tsx` - 页面集成测试
- [x] T058 验证粒子动画帧率不低于 30 FPS
- [x] T059 验证数据刷新响应时间不超过 2 秒
- [x] T060 验证页面加载时间不超过 3 秒

## Phase 7: Polish & Documentation（预计 1 小时）

- [x] T061 细节打磨（颜色、间距、动画效果）
- [x] T062 验证深色主题视觉效果（对比度、色彩搭配）
- [x] T063 验证所有动画效果流畅（粒子、数字、图表、流光边框）
- [x] T064 创建 `specs/071-data-vision/README.md` - 实施文档
- [x] T065 更新主 `README.md` - 添加数据大屏说明
- [x] T066 验证 TypeScript 编译通过（`npx tsc --noEmit`）
- [x] T067 验证单元测试全部通过（`npx vitest run`）
- [x] T068 验证集成测试全部通过
- [x] T069 更新 tasks.md 标记所有任务完成

## 依赖关系

```
Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5 → Phase 6 → Phase 7
```

- Phase 1 是基础设施，必须最先完成。
- Phase 2 依赖 Phase 1 的基础组件。
- Phase 3 依赖 Phase 1 的基础组件和 Phase 2 的数据集成。
- Phase 4 依赖 Phase 3 的图表组件。
- Phase 5 依赖 Phase 4 的全屏和响应式布局。
- Phase 6 依赖 Phase 5 的页面集成。
- Phase 7 依赖 Phase 6 的性能优化和测试。

## 验收标准

- [x] 所有任务标记为完成（`[x]`）
- [x] TypeScript 编译通过（`npx tsc --noEmit` 无错误）
- [x] 单元测试覆盖率不低于 80%
- [x] 集成测试全部通过
- [x] 性能测试通过（帧率不低于 30 FPS，加载时间不超过 3 秒）
- [x] 兼容性测试通过（Chrome 90+、Edge 90+、Firefox 88+、Safari 14+）
- [x] 视觉效果酷炫（深色主题、粒子动画、流光边框、霓虹文字、图表动画）
- [x] 全屏展示功能正常（进入/退出全屏、ESC 键退出）
- [x] 响应式布局正常（3/4/5 列切换、字体和图表尺寸自适应）
- [x] 数据自动刷新正常（30 秒间隔）
- [x] 手动刷新按钮正常
