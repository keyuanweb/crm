# 快速开始：使用地图

## 前端

1. `pnpm add @antv/g6`（v5）。
2. `types/usageMap.ts`：FlowDef/QuickAction + 流程数据常量。
3. `pages/map/UsageMapPage.tsx`：G6 流程图（主流程/角色 Tabs 切换/节点点击跳转/高频入口/渲染降级）。
4. `DashboardPage` 欢迎区加入口按钮（"查看使用地图"）。
5. `App.tsx` 注册路由 /usage-map。

## 验证

- `pnpm run typecheck` + `lint` + `test`（UsageMapPage 渲染测试 mock G6 + 首页入口测试）。
- 冒烟：首页入口 → 使用地图 → 主流程图渲染 → 点击节点跳转 → 角色切换 → 高频入口跳转。

## 备注

- G6 v5 API（Graph.init/render/destroy）。
- 无后端改动；流程定义前端常量。
