---
description: "任务清单：列表页内容区撑满（090，事后立项回填）"
---

# 任务清单：列表页内容区撑满（090）

**Input**: Design documents from `/specs/090-list-page-fill-height/`
**Prerequisites**: [spec.md](./spec.md)（必需）、[plan.md](./plan.md)（必需）
**Tests**: **不新增单测** —— jsdom 无布局引擎，几何判据写进单测只会得到假绿灯（spec.md 非目标、plan.md 决策 10）。
本项的判据是**浏览器实测**与**四道门禁**。

**Organization**: 按「回填已交付 → 验证取证 → 登记 → 遗留」分组。

## 格式：`[ID] [P?] 说明`

- **[P]**：可并行（不同文件、无未完成依赖）
- 每项任务均含**确切文件路径**
- **实测证据一律内嵌日期**；口径有边界的，边界一并写明

> **编号消歧**：本文档的 `T0xx` 属于本规格，与 `specs/083-*`、`085-*`、`086-*`、`087-*`、`088-*` 的同名编号**无关**。

> **⚠️ 两项如实告知（不得省略）**
> 1. **本规格是事后立项**：T001–T005 的代码在规格之前就已落地，靠提交信息与 `index.css` 注释留痕；
>    本文件是**回填**，不是前置规划。读本文件者不得据此认为本项走过 spec-first 流程。
> 2. **范围先窄后全**：计划阶段把「Tabs 内嵌的 5 张表」明确划为**不做**并如实告知用户；
>    用户口径是「全部列表页」，故 T005 事后补上。这是一次**获批的缺口**，不是偷偷缩范围，也不是偷偷扩范围。

## 路径约定

- **前端**：`frontend/src/`
- **后端**：**只读** —— 本项零后端改动、无 Flyway 迁移、无契约变更

---

## Phase 1: 已交付（回填）

- [x] **T001** `frontend/src/App.tsx`：`<Content>` 加 `display: flex; flexDirection: column`；
  `.page-container` 去掉 `minHeight: 'calc(100vh - 48px - 40px)'`（FR-007）。
  **实测**：`/data-retention` 滚动盒 `scrollHeight 1052 → 1016 == clientHeight`，幽灵滚动消除。
  （提交 `f75496a`）
- [x] **T002** `frontend/src/index.css`：新增「列表页：表格卡片撑满内容区剩余高度」整段 ——
  入口 A（`.page-fade > .ant-pro-table` / `.page-fade > div:has(> .ant-pro-table)`）、
  入口 B（`.page-fade > .ant-card:has(.ant-table, .ant-tree, .ant-list)`），
  以及「卡片 → 卡片体 → `.ant-table-wrapper` → `.ant-spin-nested-loading` → `.ant-spin-container` → `.ant-table`」
  逐层链条（FR-001/002/003/008）。**每层两支并列写、全部 `>` 直接子选择器。**
- [x] **T003** `frontend/src/index.css`：`.page-container .ant-breadcrumb { margin-bottom: 0 !important }`
  —— 收掉与外层重复的内层 12px（flex 列不合并嵌套 margin）。**实测**：`.page-fade` 顶边由 113 回到 **101**（与改动前一致）。
- [x] **T004** `frontend/src/index.css`：`.ant-pro-table-search .ant-row .ant-form-item` 等两条覆盖规则，
  让查询表单不再继承通用 24px 下边距（FR-006）。
  **实测**：19 个列表页的查询卡片高度 **104 → 80px**。
- [x] **T005** `frontend/src/index.css`：入口 C —— Tabs 高度四层传播
  （`.ant-tabs` → `.ant-tabs-content-holder` → `.ant-tabs-content` → `.ant-tabs-tabpane`）
  + 从 tabpane 往下的两支链条；tabpane 判据带 `:not(.ant-tabs-tabpane-hidden)`（FR-005）。
  **实测**：`/tags` 792→56、`/marketing/email` 724→56、`/open-platform` 682→56；
  切到页签 2 后卡片底边仍 1044，未选中页签 `display: none`。
  **途中真实回归**：初版未排除 `.ant-tabs-tabpane-hidden`，两个页签同时铺开、各占 441px，已修。
  （提交 `d60ad0f`）

---

## Phase 2: 验证与取证

- [x] **T006** 门禁四道（SC-005，2026-09-14 实测）：
  `pnpm run typecheck` 退出 0 · `npx eslint .` 退出 0 ·
  `pnpm run test` **83 个测试文件 / 376 例全过** · `pnpm run build` ✓ 11.03s。
- [x] **T007** 浏览器实测取证（SC-001~004，2026-09-14，Playwright 打真实 dev server）：
  - 1280×1100：`/quotas` `/orders` `/roles` `/users` 卡片底边 **1044** = 内容盒底、分页顶 **1004**；
    `/data-retention` `/exports/scheduled` `/departments` 空白 **72**（含卡片自带 16px 外边距）
  - 1280×720：`/quotas` `/orders` 卡片底边 **664** = 内容盒底，`scrollH == clientH`
  - `/customers`（19 行）：行数不变、末行底 989、分页顶 1005、表格内部 overflow **0**（无裁切）
  - **parity（不该变的页面）**：`/quotas/create` `.page-fade` [101,713]、`/stats` −373、
    `/account/password` [113,576]、`/personal-center` [101,577] —— 与改动前逐项相同
  - 375×812：`clientWidth 24` / 容器宽 0，**改动前后相同**（既有缺陷，见 T010）
- [x] **T008** 临时探针全部删除（共 15 个：7 个 `frontend/__probe-*.mjs`、
  4 个 `frontend/e2e/__*.spec.ts`、4 个本轮的 `__probe-tabs*.mjs`）——
  它们都是 untracked，不进仓库（e2e 目录下的会被 `pnpm test:e2e` 收走，必须删）。
- [x] **T009** 把几何判据固化为**可复跑的取证脚本**并入仓库：
  `specs/090-list-page-fill-height/measure-fill-height.mjs`（复现 spec.md 的实测表：
  逐页 `gap` / 滚动盒 / 卡片底边 / 分页顶边 / 行数 / 表格内部 overflow，含 Tabs 页的页签切换）。
  **实测（2026-09-14 复跑）**：`cd specs/090-list-page-fill-height && node measure-fill-height.mjs`
  —— `/quotas` `/orders` `/roles` `/users` gap 56 / cardBot 1044 / pagerTop 1004；
  `/data-retention` `/exports/scheduled` `/departments` gap 72；Tabs 三页两个页签均 cardBot 1044、
  `hidden=["none"]`；parity 四页 fade 与 gap 与上表一致。
  **口径**：脚本只读、只打 dev server，不写库、不改代码；与 088 的 `measure-ui-baseline.mjs` 同形制。
  **为什么值得入库**：本项的核心结论全是「量出来的」，没有脚本则数字不可复现、后人只能重走。

---

## Phase 3: 登记

- [x] **T010** 登记：`specs/README.md` 模块表加 090 行 + 版本行的迁移/契约声明补 090；
  `specs/roadmap.md` 的「当前进度」与覆盖度加 090。
  **口径**：090 为**改造类**（改生产代码），**无迁移、无后端改动、无契约变更**，
  故迁移对照表**不新增行**。
- [x] **T011** `.specify/feature.json` 指向 `specs/090-list-page-fill-height`
  （该文件是 gitignore 的**共享单槽指针**，并行会话会互相覆盖，故改动前后均须核对）。

---

## Phase 4: 遗留（本项不做，明确留给后续）

- [ ] **T012** **767px 以下窄屏的内容区塌陷**（本项非目标）：实测 375×812 下
  `.page-scroll` 的 `clientWidth` 只剩 24px、`.page-container` / `.page-fade` 宽为 0，
  **改动前后逐项相同** ⇒ 既有全站缺陷，非本项引入。修它要同时动 antd 的 `has-sider` 布局规则
  （与「jsdom 恒 1024 宽导致窄屏分支零执行」同源），**须另立一项**并配窄屏可执行的判据。
  触发条件：用户提出窄屏/移动端可用性问题，或做 088 的响应式铺开时一并处置。
- [ ] **T013** **列表页几何的端到端护栏**（可选）：本项无任何自动化用例覆盖几何，
  回归只能靠人眼。若要把 SC-001/002/003 变成机器门禁，需引入**能跑布局引擎**的 e2e
  （Playwright）并接进 `pnpm test:e2e`；**注意**：e2e 打的是**已在跑的后端**，
  写证据前必须比对进程启动时间与 `.class` mtime，否则会把"别人的后端"当成自己的验证。
