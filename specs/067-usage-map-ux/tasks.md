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

---

## ⚠️ 订正（2026-09-16）：真缺口 **2 → 1**（且这 1 条已落地）

由 `specs/095-ux-gap-closeout` 写入。**本文件上面所有原文与勾选行一律保留、不回写。**

### 一、原清单第 2 条（T001「逐节点默认样式值」）**撤下** —— 它不是缺口

**实测**（口径同上：锚在**生产代码**的字符串，不用「测试里提到某字面量」当证据）。
判据锚在**「有无读取方」**而不是「这个词出现过几次」—— 二者在这三个字段上**结论正好相反**：

```
$ grep -rn "shadow?\|borderRadius?\|hoverHighlight?" src/types/usageMap.ts
30:  shadow?: string
32:  borderRadius?: number
34:  hoverHighlight?: string          ← 三个字段只有**声明处本身**

$ grep -rnE "\.(shadow|borderRadius|hoverHighlight)\b" src/ | grep -v types/usageMap.ts
   → **0 命中**：全仓**没有任何地方读**这三个字段（属性访问形态）

$ grep -nE "^\s*(shadow|borderRadius|hoverHighlight):" src/types/usageMap.ts | grep -v "?"
   → 空：连**声明处所在的文件**里也没有赋值（三个字段只有 `?` 声明）

$ grep -rn "hoverHighlight" src/      → 1 命中（就是上面那行声明）
$ grep -rn "borderRadius" src/ | wc -l → **76**
$ grep -rn "shadow" src/ （排除声明）  → **8**（UsageMapPage.tsx 的 G6 样式对象 + 其测试）
```

**⚠️ 后两行的读数要读对**：`borderRadius` 一词在全仓出现 **76 次**、`shadow` 出现 8 次，
但它们**全部是别的东西的同名键** —— 组件内联样式的 `borderRadius`（`App.tsx` / `Highlight.tsx` …）
与 G6 节点样式对象里的 `shadow:`（`UsageMapPage.tsx:101/149/157`，那是喂给 G6 的样式键，
**不是** `FlowNode.shadow`）。
判据是**属性访问形态**（`node.shadow` / `.borderRadius` / `.hoverHighlight`）**0 命中** ⇒
**无读取方**。（这一处刻意记下来：本仓有过「用『字面量出现过』当『有人用它』」的口径事故，
反过来也成立 —— 用「这个词出现过」去否定孤儿结论同样是错的口径。）

**三条理由**：

1. 这三个字段**没有任何消费者**（无赋值、无读取）⇒ 给它们补「默认样式值」等于造**死数据**；
   088 刚清掉一批真孤儿，本仓的口径是不再新造。
2. `spec.md` 的 FR-001 判据（节点有阴影/圆角/悬停高亮）**已由页面级统一默认样式满足**
   （`UsageMapPage.tsx` 的 `shadow` / `radius` / `node:mouseenter`，即上表 T002 那行），
   而 **`spec.md` 全文没有「逐节点」的要求**。
3. 原判定自己写的就是「**形态不同**」，而入真缺口清单的条件是「**规格内、且无记录在案取舍**」——
   「规格无此要求」⇒ **不满足入清单条件**。

### 二、原清单第 1 条（T009 的红色警示用例）**已落地**（2026-09-16）

落点：`frontend/src/pages/map/UsageMapPage.test.tsx` 的用例
`状态流转的异常节点带红色警示（067 T009）`（**纯测试补齐，生产代码一字未改**）。
它同时守住两半：`warning` ⇒ 红边框 + 粗边框的**映射**，以及该视图的节点数据里
**真的有** `warning: true` 的节点 —— 少了后半条，映射永不触发，警示等于不存在。

**两处定向破坏留痕**（逐字失败信息见 `specs/095-ux-gap-closeout/falsification-evidence.md` §I）：

- 去掉 `stroke` 的 warning 分支 ⇒ 断言红在 **`cs7`**：它**自带**的 `color` 就是灰的（`#8c8c8c`），
  所以「灰变红」这一步**只能**由 warning 分支产生，蒙不对（拿自带红色的 `cs6` 去测会假绿）；
- 悬停移出恢复成一律 `lineWidth: 2` ⇒ 断言红；**而既有的「节点悬停效果」用例在同一缺陷下照旧绿**
  （它只喂非 warning 节点 `a1`）—— 即新用例**不是重复覆盖**，而是补上了既有用例**结构上够不到**的那一半。

### 三、本文件的 `0/11` 现在的读法

真缺口：**2 →（本次订正）1 →（095 落地）0** —— 落地的那条**只补了测试**，生产代码未动。
**勾选行仍不回写**（回填 `[x]` 等于把历史改写成真话）。
