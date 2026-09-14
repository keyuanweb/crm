# 功能规格：列表页内容区撑满（表格卡片吃满剩余高度）

**Feature Branch**: `090-list-page-fill-height`
> **⚠️ 订正（2026-09-14）：上行的分支名是 SDD 模板的约定字段，本仓从未真的创建过它。**
> 实测：本地只有 `master` 与工作分支 `appmod/java-upgrade-20260912235322` 两条分支，
> **083–089 的提交全部落在后者**，088/089 的 `spec.md:3` 同样情形（089 已就此写过订正）。
> 本规格的产出同样落在 `appmod/java-upgrade-20260912235322`，**不新建分支**。

**Created**: 2026-09-14
**Status**: **已交付（回填）** —— 本规格是**事后立项**，代码先落地、规格后补，
原因与范围见「本规格的由来」。它**不是** spec-first 的产物，不得据此声称本项走过前置规格流程。
**Input**: User description: "表格查询底部空白太多了（`/customers`，空白在**卡片内部**、颜色像白不像灰）。
线索两条：`.ant-pro-query-filter-row { row-gap: 24px } 应该是这个影响了`、
`.ant-row .ant-col .ant-form-item, .ant-row .ant-form-item { margin-bottom: 24px !important } 查询的表单不需要这个`。"

## 本规格的由来（回填说明）

改动先以 Claude 计划（非 `/speckit-*` 产物）落地，产出两个提交：

| 提交 | 内容 |
|---|---|
| `f75496a` | `frontend/src/index.css` +`frontend/src/App.tsx`：容器链打通到表格卡片；查询表单去掉继承的 24px；面包屑内层 12px 收口 |
| `d60ad0f` | `frontend/src/index.css`：补齐 Tabs 内嵌列表页（`/tags`、`/marketing/email`、`/open-platform`） |

事后盘点发现这是一次**改造类生产代码改动却无任何规格归属**（`088` 的非目标里没有「列表页撑满」，
全库 `specs/*/{spec,plan,tasks}.md` 亦无一处引用它）。经项目负责人裁决：**立项 090 回填**，
把已交付部分按实测值登记、把未做部分列为任务。**本规格不改写历史，两个提交的原文证据保留在提交信息里。**

---

## 背景：要解决的是什么

列表页的表格/卡片内容结束后，到页脚之间留着一大片空白，短内容页尤其明显。

**实测（Playwright 打真实 dev server，1280×1100 视口，改动前）**：

| 页面 | 页面根结构 | 视口下方空白 |
|---|---|---|
| `/data-retention` | `.page-fade > .ant-card` | **754px** |
| `/exports/scheduled` | `.page-fade > .ant-card` | **706px** |
| `/departments` | `.page-fade > .ant-card`（Card + Tree，无表格） | **676px** |
| `/quotas`（1280×720 视口） | `.page-fade > div > .ant-pro-table` | **197px** |
| `/orders`（1280×720 视口） | `.page-fade > .ant-pro-table` | **279px** |
| `/tags` | `.page-fade > .ant-tabs > … > .page-stack > .ant-pro-table` | **792px** |
| `/marketing/email` | `.page-fade > .ant-tabs > … > .ant-pro-table` | **724px** |
| `/open-platform` | `.page-fade > .ant-tabs > … > .ant-pro-table` | **682px** |

### 根因（逐层实测得出，不是推断）

`<Content>` 的高度恒等于 `100vh − Header(48) − Footer(≈36)`，与页面内容多少无关；
而**从 `.page-container` 往下全部是「文档流 + 自动高度」**——`index.css` 在本项之前
`min-height` / `max-height` / `calc(100vh` / `height:100%` / `vh` **零命中**，它完全不参与高度链条。
于是表格只按内容长，剩下的可用高度就露成 `<Content>` 的背景色 `#f0f2f5`。

**为什么用户读成「白色」**：`#f0f2f5` = RGB(240,242,245) 与卡片的 `#fff` 只差 15/255，
像素采样证实卡片内是 (255,255,255)、卡片外是 (240,242,245)。用户口径「是白色的，不是灰色的」与此吻合。

### 顺带修掉的两处既有缺陷（都在同一片区域，一并如实登记）

1. **幽灵滚动**：`App.tsx` 的 `minHeight: 'calc(100vh - 48px - 40px)'` 里那个 `40px` 是 Content 的上下 padding，
   **Footer 的约 36px 没有被减掉**，于是 `.page-container` 恒定比 Content 内容盒高约 36px ——
   每个列表页都白白多出约 36px 可滚动区域（实测 `/data-retention`：`scrollH 1052` / `clientH 1016`）。
2. **查询表单末行 24px 死白**：全库有一条 `.ant-row .ant-col .ant-form-item, .ant-row .ant-form-item { margin-bottom: 24px !important }`，
   而 ProTable 的查询表单**自带行距** `.ant-pro-query-filter-row { row-gap: 24px }`；两者叠加后，
   查询卡片末行下方恒多 24px 死白（换行时行距还会变 48px）。实测改前每一页查询卡片都是
   **104px = 24 上内边距 + 56 行 + 24 下内边距**，而 `56 = 32px 输入框 + 24px 外边距` —— 卡片里一半是空白。

---

## 已定决策（本规格开工前的问答结论）

| # | 决策项 | 结果 |
|---|---|---|
| 1 | 「底部空白」指哪一片 | **表格下方那片灰**（不是页脚、不是侧边栏） |
| 2 | 适用范围 | **全部列表页** |
| 3 | 覆盖形态 | **ProTable + 卡片内嵌表格**（Tabs 内嵌的表格事后一并补齐） |
| 4 | 卡片内还是卡片外 | **卡片内部**（用户口径「是白色的，不是灰色的」，与像素采样一致） |
| 5 | 改在哪一层 | **共用层改一处**，不做逐页改 |
| 6 | 两条 CSS 线索如何处置 | 判定为**同一处修复**：真正吃掉 24px 的是 `!important` 那条 margin；`row-gap` 在 1280 宽下**贡献 0**（19 个查询表单全是单行，只在换行时才起作用），保留它作为行距来源 |

---

## 用户场景与测试 *（必填）*

### 用户故事 1 - 打开一个列表页，表格卡片下不再有一大片空白（优先级：P1）

作为**每天要在十几个列表页之间来回的销售**，我打开一个只有一两行的列表时，
卡片应当一直铺到内容区底部、分页就在卡片底边，而不是在半空中截断、下面留一大片浅色。

**为什么是 P1**：这是用户直接报上来的问题原话，也是本项唯一的动因。

**独立测试**：在 1280×1100 视口打开 `/quotas`（1 行）、`/orders`（2 行）、`/data-retention`、`/departments`，
量「内容最低点」到「视口底部」的距离。

**验收场景**：

1. **Given** 一个短内容的 ProTable 列表页，**When** 页面加载完成，
   **Then** 表格卡片的下边界 = 内容区（`.page-scroll` 内容盒）的下边界 1044，
   分页顶边落在卡片底部附近（实测 1004），视口下方只剩 Content 自身的 20px 下内边距 + 36px 页脚。
2. **Given** 卡片作页根的列表页（`.page-fade > .ant-card`），**When** 加载完成，
   **Then** 空白 = 卡片自带的 16px 外边距 + 20 + 36 = **72px**，不再是 676–754px。

---

### 用户故事 2 - Tabs 里的列表页也一样（优先级：P1）

作为**在「标签与细分」「邮件营销」「开放平台」这几个页面上工作的用户**，
这些页面的表格装在页签里，我不希望它们被漏掉——同样是列表页，同样不该留白。

**为什么是 P1**：用户口径是「全部列表页」。这 5 张表在计划阶段被**明确划为缺口并如实告知**，
事后补齐；不补则用户点到这三个页面仍会看到同一个毛病。

**独立测试**：在 1280×1100 视口打开 `/tags`、`/marketing/email`、`/open-platform`，
量卡片底边；再点到第二个页签，重复量一次，并确认未选中页签的 `display` 仍是 `none`。

**验收场景**：

1. **Given** 表格在页签里的列表页，**When** 页签 1 激活，**Then** 卡片底边 = 1044，空白 56px。
2. **Given** 同一页面，**When** 切到页签 2，**Then** 卡片底边仍 = 1044，且**只有当前页签可见**
   （未选中页签 `display: none`，不允许出现两个页签同时铺开）。
3. **Given** 某个页签的内容不是列表（如 `/open-platform` 页签 2 是一个按钮），
   **When** 切到它，**Then** 它**不吃余量**、保持内容高度（实测 `display: block`，高 32px）。

---

### 用户故事 3 - 长表格不能被压扁或裁掉（优先级：P1）

作为**看客户列表（19 行）的人**，我要的是「短列表不留白」，不是「所有表格都塞进一屏」——
行数一行不少、不被 `.ant-table{overflow:hidden}` 裁掉、分页仍在表格下方。

**为什么是 P1**：这是本项唯一的**破坏性风险**：`.ant-table { overflow: hidden }` 存在，
一旦让表格参与 flex 收缩，长表格会被**静默裁掉**。

**独立测试**：`/customers`（19 行，内容 1041px > 视口）逐项比改动前的行数、最后一行底边、分页位置。

**验收场景**：

1. **Given** 内容高于视口的列表页，**When** 加载完成，
   **Then** 行数不变（19）、最后一行完整可见、`.ant-table-content` 自身 `scrollHeight − clientHeight = 0`（无内部裁切）。
2. **Given** 同上，**When** 量页面滚动，**Then** `scrollH ≥ clientH`（该滚就滚），不出现第二条滚动条。

---

### 用户故事 4 - 查询表单不再让卡片里一半是空白（优先级：P2）

作为**每次都要用查询条件的人**，查询区应当只占它需要的高度。

**独立测试**：量查询卡片（`.ant-pro-table-search`）的高度。

**验收场景**：

1. **Given** 任意列表页的查询表单（1280 宽，单行），**When** 加载完成，
   **Then** 查询卡片高度 = **80px**（改前 104px），即 `24 上内边距 + 32 行控件 + 24 下内边距`。
2. **Given** 查询表单在窄屏换行，**When** 换行发生，**Then** 行距由 `row-gap: 24px` 提供（不是 48px）。

---

### 边界场景

- **非列表页（表单页、仪表盘、居中页）**：**必须与改动前逐像素一致**——实测
  `/quotas/create` `.page-fade` [101,713]、`/stats` 视口下方 −373、`/account/password` [113,576]、
  `/personal-center` [101,577]，四项与改动前完全相同。
- **展开行里内嵌的第二张表**（`mail/MailSyncPage.tsx` 的 `expandedRowRender`）：
  它不在 `.page-fade` 的直接子级链上，**必须不被波及**（这是全部链条一律用 `>` 直接子选择器的原因）。
- **未选中页签被重新显示**：见 US2/AC2 —— 实测过一次真实回归（两个页签各占 441px 同时铺开），
  修法是排除 `.ant-tabs-tabpane-hidden`。
- **`@media (max-width: 767px)` 窄屏**：375×812 视口下 `.page-scroll` 的 `clientWidth` 只有 24px、
  `.page-container` / `.page-fade` 宽度为 0 —— **改动前后逐项相同**，故这是**既有**的全站缺陷，
  **不是本项引入**，本项不修（见 tasks.md 的遗留任务）。
- **不支持 `:has()` 的浏览器**（Chrome < 105 / Safari < 15.4）：整段规则不生效，退回改动前的布局，
  **不会画坏**。本库无 PostCSS/autoprefixer，`index.css` 原样发出。

---

## 功能需求 *（必填）*

- **FR-001**：列表页（ProTable 作页根、多包一层 `div`、`.page-stack`、Card 作页根、Tabs 内嵌）的表格卡片，
  其下边界必须等于内容区内容盒的下边界；不足一屏时由卡片吃满余量，超过一屏时由页面滚动。
- **FR-002**：分页（`.ant-pagination`）在卡片吃满余量后自然落到卡片底部；**不得**通过改它的 `margin` 实现。
- **FR-003**：全程**不得**在高度链上写 `min-height: 0`，保持 flex 项默认的 `min-height: auto`
  —— 因为本文件已有 `.ant-table { overflow: hidden }`，一旦允许收缩，长表格会被静默裁切。
- **FR-004**：改动只作用于「页面里确实有列表卡片」的页面；表单页、详情页、仪表盘、居中页的几何**不得变化**。
- **FR-005**：Tabs 内嵌列表页：高度须经 `.ant-tabs → .ant-tabs-content-holder → .ant-tabs-content → .ant-tabs-tabpane`
  四层下传；**未选中的页签必须保持隐藏**，非列表页签不吃余量。
- **FR-006**：ProTable 的查询表单不再继承通用的 24px `form-item` 下边距；行距交给 `row-gap`。
- **FR-007**：删除 `App.tsx` 里少减 Footer 的 `minHeight: 'calc(100vh - 48px - 40px)'`，
  消除每页约 36px 的幽灵滚动。
- **FR-008**：全部链条一律使用 `>` 直接子选择器，且各支**并列书写**（不得收进带组合符的 `:is()`，
  该写法实测恒不命中）；`:has()` 内**不得嵌套** `:has()`。

## 成功标准 *（必填）*

- **SC-001**：1280×1100 视口下，短内容列表页视口下方空白 **≤ 56px**（= Content 下内边距 20 + 页脚 36），
  Card 作页根的页面 ≤ 72px。改前为 197–754px。
- **SC-002**：`/customers`（19 行）行数一行不少、无内部裁切、分页仍在表格下方。
- **SC-003**：Tabs 三页（`/tags`、`/marketing/email`、`/open-platform`）在**任一页签**下卡片底边 = 1044，
  且同一时刻只有一个页签可见。
- **SC-004**：非列表页几何与改动前**逐像素一致**（四个取样页：`/quotas/create`、`/stats`、
  `/account/password`、`/personal-center`）。
- **SC-005**：`pnpm run typecheck` / `npx eslint .` / `pnpm run test` / `pnpm run build` 四道门禁全绿
  （实测 83 个测试文件 376 例全过）。

## 非目标（本项不做，明确留给后续）

- **767px 以下窄屏的内容区塌陷**：实测 375×812 下 `clientWidth 24` / 容器宽 0，
  **改动前后相同**，属全站既有缺陷（与 jsdom 恒 1024 宽导致的「窄屏分支零执行」同源）。
  修它要同时动 antd 的 `has-sider` 布局规则，**另立一项**。
- **`index.css` 816 行的全量清理**、**主题 token 化**（主色两套并存等）：属 `088-frontend-layout-consistency`。
- **列表页的其它视觉规范**（标签宽度、弹窗宽度、空/错/载三态）：属 `088`。
- **给每个列表页写渲染用例**：本项的验证方式是**浏览器实测 + 门禁**，
  不新增单测（jsdom 无布局引擎，量不到高度，写出来的断言是假的）。

## 假设

- 视口高度取 1100 作为「有多余空间」的代表场景，另以 720 验证「刚好一屏」；
  空白量的理论下限 `56 = 20(Content 下内边距) + 36(页脚)`，实测 Footer 高度随语言微调，故写作 ≈。
- 「全部列表页」的枚举口径：48 个用 ProTable 的非测试页面 + 7 个 Card 作页根的列表页 + 3 个 Tabs 内嵌页，
  已逐一实测根结构（42 页 `.ant-pro-table` 直挂 `.page-fade`；6 页多包一层 `div`；4 页为 `.page-stack`）。
