# Implementation Plan: 使用地图视觉样式优化

**Feature**: [spec.md](./spec.md)

**Created**: 2026-01-15

**Status**: Draft

## 概述

优化使用地图（UsageMapPage）的视觉样式，提升流程图清晰度、节点交互反馈、状态流转可视化和快捷入口美观度。

## 技术栈

- **前端框架**: React 18.2.0 + TypeScript
- **UI 组件库**: Ant Design 5.22.0
- **图表库**: AntV G6 v5
- **样式方案**: CSS-in-JS（内联 style）+ Ant Design 主题变量

## Technical Context

### 现有技术栈

| 组件 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 前端框架 | React | 18.2.0 | 客户端渲染 |
| UI 组件库 | Ant Design | 5.22.0 | 组件库 + 主题系统 |
| 图表库 | AntV G6 | v5 | 流程图渲染 |
| 状态管理 | Zustand | latest | 全局状态 |
| 路由 | React Router DOM | 6.20+ | 客户端路由 |
| 构建工具 | Vite | latest | 开发服务器 + 打包 |

### 技术约束

- **G6 v5 API**: 节点样式通过 `node.style` 配置，事件通过 `graph.on()` 注册
- **Ant Design 5 主题**: 使用 Token 机制，支持 `ConfigProvider` 配置
- **响应式断点**: `xs: <576px`, `sm: ≥576px`, `md: ≥768px`, `lg: ≥992px`, `xl: ≥1200px`
- **TypeScript**: 严格模式，无 `any`（除必要降级）

### 未知项

| 未知项 | 风险 | 缓解措施 |
|--------|------|----------|
| G6 v5 是否支持 `shadow` 样式属性 | 中 | 测试 G6 v5 文档，如不支持则降级到 `lineWidth` 模拟 |
| G6 v5 `updateItem` API 是否支持悬停样式更新 | 低 | 查阅 G6 v5 文档，或使用 `graph.refreshItem()` |
| 移动端 Drawer 默认滑出方向 | 低 | 使用 `placement="bottom"` 明确指定 |

## Constitution Check

### 原则一：契约优先的 API 设计

- **合规**: ✅ 本功能为纯前端 UI 优化，不涉及后端 API 变更
- **说明**: 无新端点，无 DTO 变更

### 原则二：分层架构与关注点分离

- **合规**: ✅ 样式优化仅影响表现层（UsageMapPage.tsx），不修改业务逻辑
- **说明**: 节点点击跳转逻辑保持不变，仅增加弹窗展示

### 原则三：数据完整性、安全与校验

- **合规**: ✅ 无数据变更，无安全影响
- **说明**: 节点详情数据来自前端常量（FlowNode.desc），不访问后端

### 原则四：测试优先与质量门禁

- **合规**: ✅ 新增单元测试（悬停效果、弹窗打开/关闭）
- **说明**: 保留现有测试，确保无回归

### 原则五：简洁、可维护与可观测

- **合规**: ✅ 样式优化保持代码简洁，无新增依赖
- **说明**: 使用 G6 原生事件和 Ant Design 组件，无额外库

## Gates

| Gate | 状态 | 说明 |
|------|------|------|
| 无后端 API 变更 | ✅ 通过 | 纯前端 UI 优化 |
| 无新增依赖 | ✅ 通过 | 使用现有 @antv/g6 和 antd |
| 无破坏性变更 | ✅ 通过 | 现有功能保持不变 |
| 测试覆盖 | ✅ 通过 | 新增测试 + 保留现有测试 |

## 实施步骤

### Phase 1: 节点样式增强

**目标**: 提升流程图节点的视觉层次和交互反馈

**改动**:

1. **修改 `types/usageMap.ts`**:
    - `FlowNode` 接口新增 `shadow?`, `borderRadius?`, `hoverHighlight?` 字段（无 hoverScale，悬停无尺寸变化）
    - 为现有节点配置默认样式值

2. **修改 `pages/map/UsageMapPage.tsx`**:
    - G6 节点配置新增 `shadow`（`0 2px 8px rgba(0,0,0,0.15)`）
    - 节点圆角改为 12px（原 8px）
    - 节点尺寸微调（125×42 → 130×46）
    - 文字字号微调（13px → 14px）
    - 新增 `node:mouseenter` 事件：阴影加深 + 边框高亮（无尺寸变化）
    - 新增 `node:mouseleave` 事件：节点恢复原状

**影响范围**: `types/usageMap.ts`, `pages/map/UsageMapPage.tsx`

### Phase 2: 节点详情弹窗

**目标**: 点击节点弹出详情卡片，展示操作要点和跳转入口

**改动**:

1. **修改 `types/usageMap.ts`**:
    - 无需新增 actions 字段（使用现有 desc）
    - 确保现有节点 desc 内容完整

2. **修改 `pages/map/UsageMapPage.tsx`**:
    - 新增 `Modal`/`Drawer` 状态管理（`modalOpen`, `selectedNode`）
    - 节点点击事件改为：打开详情弹窗
    - 弹窗内容：标题、描述（FlowNode.desc）、跳转按钮
    - 移动端改用 `Drawer`（使用 `Grid.useBreakpoint()` 判断）

**影响范围**: `pages/map/UsageMapPage.tsx`

### Phase 3: 状态流转可视化增强

**目标**: 状态流转图用颜色区分状态类别，异常状态有红色警示

**改动**:

1. **修改 `types/usageMap.ts`**:
    - `STATE_FLOWS` 中各状态节点新增 `warning?` 字段（标识异常状态）
    - 已驳回/已终止等节点设置 `warning: true`

2. **修改 `pages/map/UsageMapPage.tsx`**:
    - 状态流转节点渲染时，`warning` 节点加红色边框（`lineWidth: 3`）+ 红色图标（如 `ExclamationCircleOutlined`）
    - 连线颜色增强：通过/成交绿色更鲜艳，驳回/失败红色更醒目

**影响范围**: `types/usageMap.ts`, `pages/map/UsageMapPage.tsx`

### Phase 4: 快捷入口样式优化

**目标**: 快捷入口按钮更美观，有图标+文字组合和悬停反馈

**改动**:

1. **修改 `pages/map/UsageMapPage.tsx`**:
    - 快捷按钮改为 `Button` 组件，`icon` + `children`（文字）
    - 按钮样式：`height: 48px`, `borderRadius: 10px`, `background: #f5f5f5`
      > ⚠️ **订正（2026-09-14，088/T053）**：`borderRadius: 10px` 这个值**已不成立**。088 的 T053
      > 把全站圆角收敛到单一真源后，按钮族取 `var(--radius-md)`（= 8）。该按钮**已按此改**，
      > 本行原文保留作决策留痕。**订正的是取值，不是「这是按钮不是卡片」这个族属判断**——
      > 它正是该按钮**没有**跟着卡片一起变成 `var(--radius-lg)` 的理由。
    - 悬停效果：`background: #e6f4ff`, `color: #1677ff`（使用 CSS `:hover` 或 Ant Design `styles={{ body: {} }}`）
    - 按钮间距调整为 `gap: 12px`

**影响范围**: `pages/map/UsageMapPage.tsx`

### Phase 5: 响应式适配

**目标**: 确保移动端体验良好

**改动**:

1. **修改 `pages/map/UsageMapPage.tsx`**:
    - 使用 `Grid.useBreakpoint()` 判断移动端
    - 移动端：节点尺寸缩小（130×46 → 110×38）、文字缩小（14px → 12px）
    - 移动端：节点详情改用 `Drawer`（从底部滑出）
    - 移动端：快捷按钮改为 2 列网格布局

**影响范围**: `pages/map/UsageMapPage.tsx`

### Phase 6: 测试与验证

**目标**: 确保样式优化无回归

**改动**:

1. **修改 `pages/map/UsageMapPage.test.tsx`**:
    - 新增测试：节点悬停效果
    - 新增测试：节点详情弹窗打开/关闭
    - 新增测试：状态流转异常状态红色警示
    - 新增测试：快捷入口悬停效果

2. **手动冒烟测试**:
    - 打开使用地图，验证主流程节点样式
    - 悬停节点，验证阴影加深/边框高亮效果
    - 点击节点，验证详情弹窗
    - 切换到状态流转，验证颜色区分
    - 点击快捷入口，验证跳转正常
    - 移动端验证响应式布局

## 风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| G6 节点样式兼容性 | 中 | 测试 G6 v5 支持的样式属性，降级到兼容方案 |
| 弹窗遮挡流程图 | 低 | 调整弹窗位置或使用 Drawer |
| 移动端性能 | 低 | 限制动画复杂度，使用 CSS transform |
| 样式回归 | 中 | 保留现有功能测试，手动冒烟验证 |

## 验收标准

- [ ] 节点有阴影、圆角、悬停交互效果（阴影加深 + 边框高亮，无尺寸变化）
- [ ] 点击节点弹出详情（标题、描述、跳转按钮）
- [ ] 状态流转图异常状态有红色警示
- [ ] 快捷入口按钮有图标+文字、悬停反馈
- [ ] 移动端响应式布局正常
- [ ] 所有现有功能（节点跳转、角色切换、状态流转切换）正常工作
- [ ] TypeScript 类型检查通过
- [ ] 单元测试通过

## 依赖

- 现有 `@antv/g6` v5 依赖
- 现有 `types/usageMap.ts` 类型定义
- 现有 `pages/map/UsageMapPage.tsx` 页面组件
