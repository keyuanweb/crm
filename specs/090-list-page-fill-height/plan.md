# 实施计划：列表页内容区撑满（090）

**Input**: [spec.md](./spec.md)（用户故事、FR-001~008、SC-001~005）
**形制**: **改造类**（与 086/088 同类——改生产代码；与 003/083/085/087/089 的「加固类」相对）
**迁移/契约**: **无 Flyway 迁移、无后端改动、无契约变更**（纯前端渲染层）

## 技术上下文

| 项 | 值 |
|---|---|
| 前端 | React 18.2 + TypeScript + Vite 5 |
| 组件库 | antd 5.22.0 + `@ant-design/pro-components`（pro-table 3.21.x / pro-card 2.10.0；**不存在** `@ant-design/pro-table` 这个包） |
| 改动文件 | `frontend/src/index.css`（主）、`frontend/src/App.tsx`（Shell 两行） |
| 浏览器支持 | `:has()` 支持基线 Chrome 105+ / Safari 15.4+；本库**无 PostCSS/autoprefixer、无 browserslist、无 build.target**，`:has()` 原样发出；不支持的浏览器退化为改动前布局 |
| 测试框架 | vitest 1.6（jsdom）——**注意：jsdom 无布局引擎，量不到高度**，本项的判据不能落在单测上 |

## 章程检查

| 原则 | 结论 |
|---|---|
| 契约优先 | **不适用**：无端点、无端点语义变更，不产 `contracts/`（与 086/087 同形制）。 |
| 分层架构 | **通过**：只改前端样式层与 Shell 的一处内联样式，不跨越层。 |
| 测试优先 | **通过（并附口径说明）**：本项判据是**几何量**，jsdom 量不到，故验证方式为「浏览器实测 + 四道门禁」，且**不新增假单测**（写一个 `expect(style.display).toBe('flex')` 只能证明选择器写对了、证明不了布局变对）。此口径已在 spec.md 非目标里写明。 |
| 文档留痕 | **通过**：改动的每一条非显然结论（`:is()` 陷阱、隐藏页签陷阱、不写 `min-height:0` 的理由）都内嵌在 `index.css` 注释里；本规格回填实测值。 |

## 技术方案

**一句话**：把「页面根 → 表格卡片 → 卡片体 → 表体」这条链上每一层从「自动高度」改成
`display:flex; flex-direction:column; flex:1 1 auto`，让 `.ant-table` 吃掉余量，分页作为它的兄弟
自然落到底部。

链路（每层都必须有规则，漏一层整条就断）：

```
.page-container → .page-fade → [入口 A/B/C] → .ant-pro-table → .ant-pro-card:not(.ant-pro-table-search)
  → .ant-pro-card-body → .ant-table-wrapper → .ant-spin-nested-loading → .ant-spin-container → .ant-table
```

三个入口（**必须并列书写，不能合并**）：

| 入口 | 判据 | 页数 |
|---|---|---|
| A | `.page-fade > .ant-pro-table` / `.page-fade > div:has(> .ant-pro-table)` | 42 直挂 + 6 包一层 div（含 4 个 `.page-stack`） |
| B | `.page-fade > .ant-card:has(.ant-table, .ant-tree, .ant-list)` | 7（数据保留 / 计划导出 / 部门 …） |
| C | `.page-fade > .ant-tabs > … > .ant-tabs-tabpane` 内装着列表卡片 | 3（`/tags`、`/marketing/email`、`/open-platform`） |

## 关键决策

| # | 决策 | 理由 / 被否决的备选 |
|---|---|---|
| 1 | **改 CSS，不改 antd theme token** | 备选是接 `ConfigProvider theme`。实测 pro-components **不消费** antd 的组件级 token（`src/theme/index.ts:87-109` 只覆盖 `Form`/`Card`/`Table`），且本项要的是**容器的盒模型**，不是颜色/尺寸 token —— 这条路走不通。（主题 token 化属 088。） |
| 2 | **不写 `min-height: 0`** | 这是 flex 收缩的开关，而 `index.css:545-548` 有 `.ant-table { overflow: hidden }`：一旦表格被允许收缩到内容以下，行会被**静默裁掉**（不报错、不滚动）。保持 `min-height: auto` 则长表格只把页面撑高。 |
| 3 | **全部用 `>` 直接子选择器** | `mail/MailSyncPage.tsx:144` 的 `expandedRowRender` 里嵌了第二张 ProTable。后代选择器会连它一起命中；`>` 链天然把它排除（它不在 `.page-fade` 的两层之内）。 |
| 4 | **各支并列写，不收进 `:is()`** | `.page-fade > :is(.ant-pro-table, div > .ant-pro-table)` 这一支**恒不命中**：`:is()` 是主体，`div >` 要求「父级是 div」，外层 `>` 又要求「父级是 .page-fade」，两条互斥。浏览器实测 `.matches('.page-fade > div > .ant-pro-table') === true` 而 `.matches('.page-fade > :is(…)) === false` —— 这正是本项命中面**变窄过一次**的原因，已写进注释。 |
| 5 | **flex 链只给「确实含列表卡片」的页面** | flex 列里**嵌套的 margin 不再合并**。不加 `:has()` 限定时，40+ 个非列表页会有 12~24px 位移（实测 `/account/password` 内容高 24px、每页整体下移 12px）。 |
| 6 | **面包屑内层 12px 收掉** | `BreadcrumbNav.tsx:28` 的 `marginBottom:12` 与 `App.tsx` 包裹 div 的 `marginBottom:12` 是父子两层：块布局合并成 12px，flex 下变成 24px ⇒ 每页下移 12px。收内层、留外层，观感与改动前一致。 |
| 7 | **查询表单的 24px 用同特异性但更靠后的选择器覆盖** | 全库那条是 `!important`。`.ant-pro-table-search .ant-row .ant-form-item` 与它同特异性、位置更靠后，故能盖住；**不删**那条通用规则（弹窗/详情页的多行表单还需要它）。 |
| 8 | **Tabs 的 tabpane 规则排除 `.ant-tabs-tabpane-hidden`** | antd 用 `.ant-tabs-tabpane-hidden { display:none }` 藏未选中页签；本规则的 `display:flex` 特异性更高，会把它**重新显示出来**——实测两个页签同时铺开、各占 441px。 |
| 9 | **非列表页签不吃余量** | tabpane 用 `:has(> .ant-pro-table, > .page-stack > .ant-pro-table)` 自判。实测 `/open-platform` 页签 2 是按钮页（`display: block`，高 32px），不受影响。 |
| 10 | **不新增单测** | jsdom 无布局引擎。真正能抓到这类回归的是**浏览器实测**（本项的探针）与 088 那类渲染层用例；写几何断言只会得到假的绿灯。 |

## 改动清单

| 文件 | 改动 |
|---|---|
| `frontend/src/App.tsx` | `<Content>` 加 `display:flex; flexDirection:column`（它只有一个子节点，改 flex 是封闭的）；`.page-container` 去掉 `minHeight: 'calc(100vh - 48px - 40px)'`（FR-007） |
| `frontend/src/index.css` | 新增「列表页：表格卡片撑满内容区剩余高度」整段（入口 A/B/C + 逐层链条 + 面包屑 12px 收口），以及「查询表单不吃 24px」两条覆盖规则 |

## 验证方式

1. **浏览器实测**（Playwright 打真实 dev server，非 mock）：逐页量
   `gapBelowContent` / `scrollHeight vs clientHeight` / 卡片底边 / 分页顶边 / 行数 / 表格内部 overflow。
   三条视口：1280×1100（有多余空间）、1280×720（刚好一屏）、375×812（窄屏，作对照）。
2. **before/after 对拍**：改动前的布局用注入样式在浏览器里还原（恢复被删的内联 `min-height` + 两级退回 block），
   再量一次，用于定位 12px / 24px 漂移的归属。
3. **波及面枚举**：所有含 Tabs 的页面逐一探针，确认只有 3 页有「根级 Tabs」。
4. **门禁**：`pnpm run typecheck && npx eslint . && pnpm run test && pnpm run build`。
5. **截图肉眼比对**：`/quotas`、`/orders`、`/customers`、`/data-retention`、`/departments`、`/roles`、`/users`
   加两个**不该变**的 `/quotas/create`、`/stats`；Tabs 三页各取页签 1 / 页签 2。

## 风险与缓解

| 风险 | 缓解 | 实测结果 |
|---|---|---|
| 长表格被压扁/裁切 | 决策 2（不写 `min-height:0`） | `/customers` 19 行完整，表格内部 overflow 0 |
| `.page-stack` 由 grid 改 flex 后统计卡漂移 | 只改「里面有 ProTable」的那层 div | 统计卡位置 101..187 两态相同 |
| 非列表页被波及 | 决策 5（`:has()` 限定） | 四个取样页几何逐项相同 |
| `:has()` 在老浏览器失效 | 失效形态是「退回改动前布局」 | 已写进 spec 边界场景 |
| Tabs 改动波及全站含 Tabs 的页面 | 判据锚在「根级 Tabs + 页签直接子级是列表卡片」 | 全库仅 3 页命中，其余 5 页无根级 Tabs |
