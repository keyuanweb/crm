# Tasks: 使用地图视觉样式优化

**Input**: Design documents from `/specs/067-usage-map-ux/`

**Prerequisites**: plan.md (required), spec.md (required), data-model.md (required)

**Tests**: 章程原则四要求测试先于实现（红→绿），本功能含前端渲染测试。

## Phase 1: 节点样式增强

- [ ] T001 [P] [US1] 前端：`types/usageMap.ts`——`FlowNode` 接口新增 `shadow?`, `borderRadius?`, `hoverHighlight?` 字段（无 hoverScale，悬停无尺寸变化）；为现有节点配置默认样式值。
- [ ] T002 [P] [US1] 前端：`pages/map/UsageMapPage.tsx`——G6 节点配置新增 `shadow`（`0 2px 8px rgba(0,0,0,0.15)`）、圆角 12px、尺寸 130×46、文字 14px；新增 `node:mouseenter`/`node:mouseleave` 事件（阴影加深 + 边框高亮，无尺寸变化）。

## Phase 2: 节点详情弹窗

- [ ] T003 [P] [US1] 前端：`types/usageMap.ts`——无需新增 actions 字段（使用现有 desc）；确保现有节点 desc 内容完整。
- [ ] T004 [US1] 前端：`pages/map/UsageMapPage.tsx`——新增 `Modal`/`Drawer` 状态管理（`modalOpen`, `selectedNode`）；节点点击事件改为打开详情弹窗；弹窗内容：标题、描述（FlowNode.desc）、跳转按钮；移动端使用 `Drawer`。

## Phase 3: 状态流转可视化增强

- [ ] T005 [P] [US1] 前端：`types/usageMap.ts`——`STATE_FLOWS` 中各状态节点新增 `warning?` 字段；已驳回/已终止等节点设置 `warning: true`。
- [ ] T006 [US1] 前端：`pages/map/UsageMapPage.tsx`——状态流转节点渲染时，`warning` 节点加红色边框（`lineWidth: 3`）+ 红色图标；连线颜色增强。

## Phase 4: 快捷入口样式优化

- [ ] T007 [P] [US1] 前端：`pages/map/UsageMapPage.tsx`——快捷按钮样式优化：`height: 48px`, `borderRadius: 10px`, `background: #f5f5f5`；悬停效果：`background: #e6f4ff`, `color: #1677ff`；按钮间距 `gap: 12px`。

## Phase 5: 响应式适配

- [ ] T008 [P] [US1] 前端：`pages/map/UsageMapPage.tsx`——使用 `Grid.useBreakpoint()` 判断移动端；移动端节点尺寸缩小、文字缩小；移动端节点详情改用 `Drawer`；移动端快捷按钮改为 2 列网格布局。

## Phase 6: 测试与验证

- [ ] T009 [P] [US1] 前端：`pages/map/UsageMapPage.test.tsx`——新增测试：节点悬停效果、节点详情弹窗打开/关闭、状态流转异常状态红色警示、快捷入口悬停效果。
- [ ] T010 前端：`pnpm run typecheck` + `lint` + `test` 全量通过。
- [ ] T011 [P] 手动冒烟：打开使用地图 → 验证节点样式 → 悬停节点 → 点击节点 → 切换到状态流转 → 点击快捷入口 → 移动端验证；修复问题。

## Dependencies & Execution Order

- T001/T002 可并行，均依赖现有代码。
- T003 依赖 T001（类型定义扩展）。
- T004 依赖 T003（弹窗实现）。
- T005/T006 可并行，依赖 T003（状态节点扩展）。
- T007 无依赖，可独立执行。
- T008 依赖 T004（响应式适配）。
- T009 在所有实现完成后执行。
- T010/T011 在所有测试完成后执行。

## Notes

- G6 v5 API（节点样式配置、事件注册）。
- Ant Design 5 组件（Modal、Drawer、Button、Grid）。
- 响应式断点：`xs: <576px`, `sm: ≥576px`, `md: ≥768px`, `lg: ≥992px`, `xl: ≥1200px`。
- 样式优化不改变现有功能逻辑（节点跳转、角色切换、状态流转切换）。

---

## 订正（2026-09-15 登记清扫）——「0/11」这个数该怎么读

本文件**一条勾都没打**，但**代码基本都在**。2026-09-15 做了一次**只读逐条复核**（口径：**生产代码里的字符串锚点**；
**不用**「测试里提到某字面量」当实现证据），账如下。**勾选行一律不回写**（原文留痕），真缺口见文末清单。

| 任务 | 判定 | 落点 / 缺什么 |
|---|---|---|
| T001 | **部分** | `types/usageMap.ts` 的 `shadow?` / `borderRadius?` / `hoverHighlight?` 三字段齐（且确无 `hoverScale`）；但字面要求的「**为现有节点配置默认样式值**」无落点——默认值由 T002 的**页面级**节点配置统一给 |
| T002 | 存在 | `UsageMapPage.tsx`：`shadow: '0 2px 8px rgba(0,0,0,0.15)'`、`radius: 12`、`[130, 46]`、`node:mouseenter` |
| T003 | 存在 | 各节点 `desc` 均非空；**未新增 `actions` 字段**——与原任务「无需新增」一致 |
| T004 | 存在 | `modalOpen` / `selectedNode` / `node:click` / `Modal`，移动端走 `Drawer` |
| T005 | 存在 | `warning: true` 6 处（cs6/cs7/qs5/ts4/os6/ls4） |
| T006 | **spec 判据已满足** | 红边框（`#cf1322` + `lineWidth: 3`）与连线增强均在；**`plan.md:135` 的「红色图标」无落点**——但 `spec.md:64` 的验收词是「有红色边框**或**图标警示」，**「或」已被前半句满足** ⇒ 按 spec **非缺口**，仅 plan 的实现细节未取该路径 |
| T007 | 存在 | `height: 48` / `#f5f5f5` / `#e6f4ff` / `color: '#1677ff'` / `gap: 12` 均在。**圆角实为 `var(--radius-md)`（=8px）而非字面 10px**——这**不是缺陷**：`plan.md:149` 已于 2026-09-14 带 ⚠️ 订正「`borderRadius: 10px` 这个值已不成立」 |
| T008 | 存在 | `Grid.useBreakpoint()` / `isMobile` / `repeat(2, 1fr)` / 移动端 `Drawer` |
| T009 | **部分** | 有「节点悬停效果」「节点详情弹窗打开/关闭」；**缺「状态流转异常状态红色警示」**；「快捷入口悬停效果」缺，但测试文件里**留有明示取舍**（`UsageMapPage.test.tsx:120`：「不验证具体悬停样式，因为 Ant Design Button 组件结构复杂」） |
| T010 | **不可锚** | 命令类任务（`typecheck` / `lint` / `test`），无产物可指；本次**未运行**，故**不给判定** |
| T011 | **不可锚** | 手动冒烟，无产物可指；本次**未执行**，故**不给判定** |

**真缺口清单（供另立一项）**：
1. **T009 的「状态流转异常状态红色警示」用例**——唯一一条**规格内、且无记录在案取舍**的缺口。
2. **T001 的「逐节点默认样式值」**——现由页面级配置统一提供，**形态不同**；按字面落地须改 `types/usageMap.ts` 的节点数据。

⚠️ 本文件的 `0/11` **不得**读成「11 项没做」，也**不得**读成「11 项都做了」——按上表逐条读。
**T006 的红色图标**按 spec 的「或」**不列为缺口**（若要与 plan 对齐，属可选的实现细节）。
