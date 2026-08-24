# Implementation Plan: 员工使用地图

**Branch**: `029-usage-map` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

## Summary

前端纯静态 feature：新增 `UsageMapPage`（AntV G6 流程图：业务主流程 + 角色操作链 + 高频入口），首页欢迎区加入口卡片；流程定义为前端常量 `types/usageMap.ts`。

## Technical Context

**Language/Version**: TypeScript/React 18（前端；无后端改动）

**Primary Dependencies**: @antv/g6 v5（新增）、antd 5、react-router

**Storage**: 无（流程定义前端常量）

**Testing**: Vitest + RTL（UsageMapPage 渲染测试，mock G6）；首页入口卡片测试

**Target Platform**: Web

**Project Type**: Web 应用（前端纯静态 feature）

**Performance Goals**: G6 画布首屏渲染 ≤ 500ms（节点数 < 30）

**Constraints**: G6 v5 API（Graph.init/render/destroy）；渲染失败降级提示；移动端拖拽缩放（G6 内置）

**Scale/Scope**: 依赖 1 个 + types 1 + 页面 1 + 首页入口卡 + 测试

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则一：契约优先的 API 设计 | 无后端 API；前端流程定义契约化 | ✅ 满足（types/usageMap.ts 定义 FlowDef 结构） |
| 原则二：分层架构与关注点分离 | 表现层纯净 | ✅ 满足（纯前端，无业务规则侵入） |
| 原则三：数据完整性、安全与校验 | 安全边界 | ✅ 满足（无新增数据；跳转受后端既有权限控制） |
| 原则四：测试优先与质量门禁 | 测试先于实现 | ✅ 满足（UsageMapPage 渲染测试 mock G6 + 首页入口测试） |
| 原则五：简洁、可维护与可观测 | 避免过度设计 | ✅ 满足（流程定义前端常量，不引后端配置） |

**结论**: 无门禁违规。

## Project Structure

### Documentation (this feature)

```text
specs/029-usage-map/
├── plan.md / spec.md / research.md / data-model.md / quickstart.md
├── contracts/（usage-map 流程定义契约）
└── tasks.md
```

### Source Code (repository root)

```text
frontend/
├── package.json                        # 修改：+ @antv/g6
├── src/types/usageMap.ts               # 新增：FlowDef/FlowNode/QuickAction + 流程数据常量
├── src/pages/map/UsageMapPage.tsx      # 新增：G6 流程图（主流程/角色切换/高频入口）
├── src/pages/map/UsageMapPage.test.tsx # 新增：渲染测试（mock G6）
├── src/pages/stats/DashboardPage.tsx   # 修改：欢迎区加入口卡片（使用地图）
└── src/App.tsx                         # 修改：注册路由 /usage-map
```

**Structure Decision**: 纯前端。G6 v5 在 useEffect 中初始化 graph（容器 div 挂载后），节点点击跳转（react-router navigate）；角色切换用 Tabs/Select 过滤流程定义；高频入口按钮组跳转；G6 渲染 try-catch，失败显示 Alert 降级。首页入口卡片放欢迎区标题右侧（"查看使用地图"按钮）。

## Complexity Tracking

> 无违规，本表留空。
