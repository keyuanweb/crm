---
description: "任务清单：前端布局规范与表单体验（088）"
---

# 任务清单：前端布局规范与表单体验（088）

**Input**: Design documents from `/specs/088-frontend-layout-consistency/`

**Prerequisites**: [plan.md](./plan.md)（必需）、[spec.md](./spec.md)（必需，用户故事来源）、[research.md](./research.md)（必需，取证底稿）

**Tests**: **必需**。FR-007 要求每个新原语一个测试文件且**必须跑到分支**。覆盖率阈值不得下调（084 T037）。

**Organization**: 按阶段分组（P0 前置 → P1 纯增量 → P2 样板 → P3 铺开 → P4 退役）。
P1 是**所有后续阶段的共同前置**，且**零 `pages/**` 改动**。

## 格式：`[ID] [P?] 说明`

- **[P]**：可并行（不同文件、无未完成依赖）
- 每项任务均含**确切文件路径**
- **实测证据一律内嵌日期**；口径有边界的，边界一并写明

> **编号消歧**：本文档的 `T0xx` 属于本规格，与 `specs/083-*`、`specs/085-*`、`specs/086-*`、`specs/087-*` 的同名编号**无关**。

## 路径约定

- **前端**：`frontend/src/`、`frontend/scripts/`
- **后端**：**只读**——本规格零后端改动、无 Flyway 迁移、无契约变更（与 085/086/087 同类）

---

## P0：前置（阻塞，必须先做）

- [x] **T001** 在**冻结的**工作区上连跑 3 次 `pnpm test:coverage`，每次前后用 `SNAP()` 校验 `src/**` 内容摘要不变。
      **实测（2026-09-13）**：四次摘要逐字相同（`0baf274cb894a13376d8947fb34535a23ed64ce2`）⇒
      "复跑期间有别的会话在改 `src/pages/**`"这个候选成因**本次被排除**。
      读数：statements **67.15** / branches **72.60–72.61** / functions **33.94** / lines **67.15**（阈值 33.6 / 47.2 / 21.4 / 33.6）。
      ⇒ **初稿的"阻塞项"解除**：`functions` 余量是 **12.54pp**，不是初稿写的约 0.9pp（成因见 research.md §1）。
- [x] **T002** 验证覆盖率门禁是**活的**（退出码 0 不能证明"检查发生了"）：
      `pnpm exec vitest run src/store/authStore.test.ts --coverage --coverage.thresholds.functions=99` ⇒
      `exit_code=1` + `ERROR: Coverage for functions (2.3%) does not meet global threshold (99%)`。
- [x] **T003** 写 `specs/088-frontend-layout-consistency/measure-ui-baseline.mjs`（每个数字**连定义一起输出**）+
      存档 `baseline-output.txt`。**末尾反空洞自检在开发中真的红了两次**，两个都是会造成整类数字失真的 bug
      （闭标签被整类丢弃 ⇒「表单级 Col = 0」；`indexOf` 取行号 ⇒ 同一行报两次）。详见 research.md §6。
- [x] **T004** 用 T003 的数据写 `research.md`：现状数字（§2）、对账表（§3）、三处订正（§4）、一次撤回（§5）。
      **「撤回」比「订正」重要**：`49 页裸 fragment / 12 页包 Card` 被 AST 重导为 **46 / 38 / 4**，旧数**废弃**（§5）。

> **P0 的第一条为什么曾是阻塞项**：本规格**每一个门禁读数都是 `functions` 的增量**，
> 而 `vite.config.ts:61-64` 自己记录了复跑不确定性。**基线不稳，"有没有回归"这句话就不可证伪。**

---

## P1：纯增量（**零 `pages/**` 改动**，单独提交）

### 主题单一真源

- [x] **T010** 新建 `frontend/src/theme/index.ts` 导出 antd `ThemeConfig`：颜色取自 `index.css:13-38` 的 `:root`
      （`colorPrimary: #6366f1` + hover/light/dark、success `#10b981`、warning `#f59e0b`、error `#ef4444`、info `#3b82f6`）；
      `fontFamily` 用 `--font-family-sans` 同一串值；组件级 `Form.itemMarginBottom: 12`、
      `Card.bodyPadding: 16`、`Card.headerHeight: 44`、`Table.cellPaddingBlockSM: 6`。
      **已核对的 token 名**：全局种子 `colorPrimary`/`colorSuccess`/`colorWarning`/**`colorError`**（不是 `colorDanger`）/
      `colorInfo`/`colorLink`/`borderRadius`/`fontSize`/`fontFamily`/`controlHeight`；
      组件级 `Form.itemMarginBottom` / `Card.bodyPadding` / `Card.headerHeight` / `Table.cellPaddingBlockSM`。
      **不接 `theme.algorithm`**，也**不显式写 `defaultAlgorithm`**。
- [x] **T011** `frontend/src/components/LocaleProvider.tsx` 的 `ConfigProvider` 接 `theme`（与既有 `locale` 并列，
      `ProConfigProvider` 不变）。`index.css` 的 `:root` **保留**但注释指向 `src/theme/index.ts` 为真源。
- [x] **T012** `frontend/src/test/renderWithProviders.tsx` 同步接线，使**测试渲染的密度与生产一致**
      （否则覆盖率测试与视觉验收看的不是同一个东西）。

### 四个原语（每个一个测试文件）

- [x] **T013** `useFormMetrics.ts` + `useFormMetrics.test.tsx`（**8 用例**）。
      `labelWidthFor(language)` **抽成纯函数**：全局 mock 把 `i18n.language` 硬编码成 `'zh'`，
      英文分支在 hook 里**不可测**；抽出来后按语言取值才有真实覆盖。
      （`isEnglish` 被删——YAGNI，且会多出一个未覆盖分支。）
- [x] **T014** `formGridStyle.ts`（纯函数）+ `FormGrid.tsx` + `FormGrid.test.tsx`（**11 用例**）。
      模板串 `repeat(auto-fit, minmax(min(100%, Npx), 1fr))`，`N` 默认 = `labelWidth + 160`。
      **渲染测试只钉 `display: grid` / 子节点数 / `data-testid`**；间距与模板串在纯函数层断言（jsdom 不反射）。
      **一条反假绿**：断言默认模板里确实含 `min(100%, …)`——少了它的版本在宽容器下表现**完全一样**，
      只有 320px 视口才炸（"样板页全绿、线上窄屏溢出"的形态）。
- [x] **T015** `formModalSize.ts`（档位表）+ `FormModal.tsx` + `FormModal.test.tsx`（**9 用例**）。
      四档 `480/640/800/960`（640 是现状事实上的默认档）；`destroyOnClose` + `okText`/`cancelText` 默认值 +
      `confirmLoading`；`handleOk` 用 `try/finally` 保证**异常时也复位 loading**。
      **`destroyOnHidden` 不得"顺手改"**（5.22.0 静默忽略未知 prop ⇒ 无报错无红测）。
      **刻意不实现**自定义 footer + Enter 提交——那是待拍板的第 4 项。
- [x] **T016** `PageState.tsx` + `PageState.test.tsx`（**7 用例**）。**只有一个组件带三个分支**
      （`loading` / `error` / `empty`），不是三个组件——更少更大的组件是刻意的，但**理由不是"函数计数余量"**
      （那条论证已随 T001 失效，见 research.md §1 的推论 2）。
      `Alert` 的 `data-testid` 能存活是**核对过**的（`pickAttrs(otherProps,{aria:true,data:true})`）。
- [x] **T017** `frontend/src/components/ui/index.ts` 导出四件套（**刻意放在既有的 `components/ui/`**，
      不新建 `src/components/form/`——第二个平行的 UI 目录正是造成"设计系统只覆盖 4/101 页"的失败模式）。
      `formGridStyle`/`formGridTemplate` **刻意不从 barrel 导出**（导出会诱使页面自己拼 `grid-template-columns`）。
- [x] **T018** `src/i18n/{zh-CN,en}.ts` 加 `common.state = { loading, error, retry }`（2 语言各 3 键）。
      **必须与 `PageState.tsx` 同一次提交**——`src/test/setup.ts` 的 mock 缺键即抛。
- [x] **T019** 因 `react-refresh/only-export-components`（`eslint.config.js:21`）的 3 处告警，
      把纯函数拆成 `formGridStyle.ts` / `formModalSize.ts` 两个文件。
      **选拆分而不是 `eslint-disable`**——本仓库 lint 此前是**零告警**，不引入本规格第一处抑制注释。

### 护栏

- [x] **T020** 写 `frontend/scripts/check-ui.mjs`（7 条规则 + 4 张白名单台账 + 逐规则反假绿 + 双向白名单校验）。
      规则分档：**R1/R4/R5/R6/R7 = error**；**R2/R3 躲在 `--strict` 后面**。
      R6 **从第一天起就带白名单**——11 处里 9 处是格式/单位示例，**不该翻译**（初稿估的是 8 处且未区分性质）。
- [x] **T021** `frontend/package.json` 加 `ui:check` / `ui:check:strict` 两个 script。
- [x] **T022** `.github/workflows/ci.yml` 加一步 `ui:check`。~~**⚠️ 受阻，未完成。**~~
      该文件当前被**并行会话 `engineering-consolidation-ci-gates` 持有未提交**（连同 `Dockerfile`、`backend/pom.xml`），
      改动它会踩进对方的在飞工作。**需用户裁决**：等对方提交后由我补，或转告对方一并加上。
      **【2026-09-14 完成：阻塞已自然解除】** 复核后确认可以落地：① `git log -- .github/workflows/ci.yml`
      显示 **`ui:check` 从未被提交过**，该文件在工区里干净、**无人持有**；② 那个会话已从 `ListAgents` 消失；
      ③ 它当时在做的其实是 **JDK-25 同步**那批改动，而 089 已实测否决升 25（改立 21）⇒ **那批改动已被放弃**，
      不会再有冲突。已在 `perms:check` 之后插入 `ui:check` 步骤并**实跑退出码 0**：白名单仍冻结 **54** 处，
      未新增违规；口径**只跑默认档**（R1/R4/R5/R6/R7 为 error）——R2/R3 是正在逐页还的债、仍躲在
      `--strict` 后面，故注释里明文写了**不能**在这里改成 `ui:check:strict`，否则会整片红。

### 验证

- [x] **T023** 门禁全跑（`specs/088-*/plan.md` 的「验证」命令）：
      `typecheck` · `lint`（**零 warning**）· `i18n:check`（**2887/2887 键**）· `menu:check`（56 items）·
      `perms:check`（63 码；8 文件 / 9 处已登记 ADMIN 判断）· `ui:check` · `test:coverage`。
      **实测（2026-09-13）**：`Test Files 76 passed (76)` / `Tests 343 passed (343)`，退出码 0
      （基线 72 / 308，差额 **+4 文件 / +35 用例**逐一对上四个新测试文件）。
- [x] **T024** 覆盖率复测并与基线比：
      **67.65 / 73.02 / 34.29 / 67.65**（基线 67.15 / 72.60 / 33.94 / 67.15）——
      **四项全部上升**，`functions` 余量 12.54pp → **12.89pp**。
      ⚠️ **口径边界**：同一次会话内 `branches` 报过 **73.08** 与 **73.02**（余量 25.8pp，无害），
      与 `vite.config.ts:61-64` 记录的非确定性同源 ⇒ **单次小数位不可当论据**，此后一律**与基线区间比**。
- [x] **T025** 确认**零 `pages/**` 改动**：`git status` 里没有 `src/pages/` 路径。
      这是 FR-012 的机器可校验证明，也是"规范不合口味就整体删掉"的保险。
- [x] **T026** **护栏自验三场景**（SC-004，逐条还原后复跑确认绿）：
      ① 探针文件 `src/zz-check-ui-probe.ts` 写 `'#1677ff'` ⇒ **R1 红，exit 1**，并打印可粘贴的白名单条目；
      ② `MailSyncPage` 白名单计数 3→4 ⇒ `【R6 白名单陈旧】… 白名单登记 4 处，实际命中 3 处`，**exit 1**；
      ③ `MIN_CANDIDATES.R6` 1→999 ⇒ `【R6 自检失败】… 本规则只解析出 11 个候选点（预期 ≥ 999）`，**exit 1**。
      三场景后均**还原并复跑转绿**。**护栏不验证自身 = 又一个"看起来有门禁"。**

> **P1 的出口判据**：**零调用点改动，全绿**（原写"72 个测试文件"，实际达成为 **76 个**）。

---

## P2：样板 4 页（**待用户先给 P1 提交授权**）

> 排序原则是**用测试风险换验证速度**：先挑缺陷密度最高、测试耦合最低的页面，
> 把 API 在简单情形上跑通再上难的。**一页一提交；页内「外壳」与「表单原语」再分两次提交。**

- [x] **T030** [P] `invoices/InvoiceListPage.tsx` —— 外壳（`.page-stack`）。
      该页是**最小页面里缺陷种类最全**的：4 个无宽度横向表单弹窗之一（`:209`）、
      3 个写死 `borderRadius:10` 的统计块（`:168-180`）、1 处 `#1677ff`（`:171`）、3 处 `/100` 分转元。
      测试耦合 **0 `within` / 0 `closest` / 2 `getByText`**（最低）。
      **✅ 完成（`f9f2f57`，2 文件 +16/−5）**：`index.css` 加 `.page-stack`（紧贴已死的 `.page-header` 块）、
      页面根 fragment → `div.page-stack`、删掉 `Row` 的 `marginBottom:16` 与手搓 spacer。
      **唯一视觉差异：统计行到表格 32px → 16px**（research.md §11.1）。
      门禁全绿：typecheck / lint / 7 项脚本检查 / 本页 3 用例。
- [x] **T031** `invoices/InvoiceListPage.tsx` —— `FormModal` + `FormGrid`，并顺手收掉该页的
      1 处 `#1677ff` 与 3 个手搓统计块（**并删掉 `check-ui.mjs` 的 R1 对应白名单条目**）。
      **✅ 完成**：两个 `Modal` → `FormModal`（创建 `md` / 作废 `sm`）、5 个字段进 `FormGrid`、
      统计块 → `StatCard` + 响应式 `Col`（`xs/sm/lg`）、标签宽度改从 `useFormMetrics()` 取（90px → 96/112）、
      删掉 `saving` state（提交中状态由 `FormModal` 的 `confirmLoading` 接管）、
      R1 白名单 33/12 → **32/11**、白名单合计 56 → **55**。
      新增 `InvoiceListPage.form.test.tsx`（5 用例）：该页此前**从不打开弹窗**，
      故 `FormModal` 的默认脚注、`onSubmit` 接线、`FormGrid` 渲染在本页是零执行的。
      **一处计划落空并经实测否决**：`/100` 一处都没收（4 处里只有 1 处可换 `AmountDisplay`，
      只换 1 处会让"表格有 ¥ 而统计块没有"——正是本批次要消除的不一致）。见 research.md §11.3。
      **两处宽度变化（验收时对照）**：创建 520（antd 默认）→ **640**（`md`，两列栅格的最低档）、
      作废 520 → **480**（`sm`）。创建那处当时**漏记**，已在 §11.7 补上。
      **一处既有缺陷被新用例抓出**：本页首屏从不拉统计（`loadStats` 只被 `reload` 调用），
      三个数字恒为 0；已在用例里钉住，**另起一次提交修**。见 research.md §11.4。
- [x] **T032 **[P] `tags/TagListPage.tsx` —— 外壳。**全库最简单的表单**（3 项）、**最窄标签（80px）**、
      第 2 个无宽度弹窗（`:121`）。测试耦合 **0/0/1**。
      **✅ 完成（1 文件 +2/−2）**：根 fragment → `div.page-stack`（`index.css` 的类已在 `f9f2f57` 加过，本页不动 CSS）。
      **本页是零视觉差异**，与 T030 不同：T030 删掉了 `Row` 的 `marginBottom:16` 与一个手搓 spacer，
      而本页根节点下**只有 `ProTable` 一个 DOM 子元素**（`Modal` 经 portal 挂到 `body`，不是本容器的子节点），
      单行 grid 上 `row-gap` 无处生效，且本页本来就没有可删的间距。故这一次是**纯约定性**改动——
      目的是让 P3 铺开时"页级外壳"有统一形态，不是为了改这页的样子。
      门禁：typecheck / lint / `src/pages/tags/` 6 用例（2 文件）全绿。
- [x] **T033** `tags/TagListPage.tsx` —— `FormModal`（先用它把 API 在最简单的情形上验证）。
      **✅ 完成**：`Modal` → `FormModal size="sm"`（原为 antd 默认 **520**，见 §11.6 同一取档理由）、
      删掉页面的 `saving` state（提交中状态由 `FormModal` 的 `confirmLoading` 接管）、
      `onOk={() => void onSave()}` → `onSubmit={onSave}`、标签宽 `80px` → `useFormMetrics()` 的 **96/112**。
      **刻意不做 `FormGrid`**（不是漏写）：`sm` 档可用宽 ≈432px，而 `minItemWidth` = 96+160 = 256
      ⇒ **只有 1 列**，即本页是唯一一次纯粹的 `FormModal` API 验证，栅格留给后三页。见 research.md §12.2。
      **一处改前就存在的裸 rejection 顺手修掉**：`onSave` 首行的 `form.validateFields()` 校验失败会抛错，
      而原 `onOk={() => void onSave()}` 无 catch ⇒ 无人接管的 promise rejection；按 T031 同一处置
      就地吃掉（`FormModal` **刻意不吞异常**）。见 §12.4。
      新增 `TagListPage.form.test.tsx`（**5 用例**）：该页此前**从不打开弹窗**，故 `FormModal` 默认脚注、
      `onSubmit` 接线、`labelCol` 的来源在本页是零执行的。**证伪力已实测**（标签宽探针 → 只有第 1 条红）。
      读数：**R2 24 → 23 处**（本页无宽度弹窗销账）、R3 仍 96、总问题数 120 → 119。
      **R3 的那处色块 `<Col span={2}>` 本提交不动**，理由见 §12.5（`FormGrid` 对它**是错的修法**），记给 T045。
- [x] **T034 **[P] `products/ProductListPage.tsx` —— 外壳。
      **本仓库自己的响应式参考实现**（`:260-289`，全库仅有的 6 个响应式表单 Col）
      ⇒ 改它等于验证 `FormGrid` **能复现既有最好行为**；若 `FormGrid` 让该页回退，**那是设计错了，不是页面错了**。
      **注意 `check-ui.mjs` 的 R6 白名单：该页 `Currency`/`Price (CNY)` 两处在册，届时一并复核**。
      **✅ 完成（1 文件 +2/−2）**：根 fragment → `div.page-stack`。与 T032 一样是**零视觉差异**
      （根下只有 `ProTable` 一个 DOM 子元素，`Modal` 经 portal 挂到 `body`），纯约定性改动。
      门禁：typecheck / lint / `src/pages/products/` 7 用例全绿。
- [x] **T035** `products/ProductListPage.tsx` —— `FormModal` + `FormGrid`，覆盖 `editing &&` 条件子区块（`:302-340`）。
      **✅ 完成**：`Modal width={640}` → `FormModal size="md"`（**宽度无变化**，640 = md 档原值）、
      6 个 `<Col xs={24} sm={12}>` → `FormGrid`、标签宽 `110px` → `useFormMetrics()` 的 96/112、
      删 `saving` state、`onOk={() => void onSave()}` → `onSubmit={onSave}`（顺带修掉一处**改前就存在**
      的裸 rejection）。`editing &&` 的多币种子区块**刻意留在 `Row`/`Col`**，理由三条见 research.md §13.5
      （首要一条：它在 `</Form>` **之外**，不是表单的一部分，没有 `Form.Item` 可包）。
      **§13.2 是本页的实质**：两种分列条件算出来比过——Col 按视口 `sm ≥576`，`FormGrid` 按容器
      `≥512`（即视口 `≥592`）⇒ 只在 `576 ≤ vw < 592` 这 16px 带里不同，那一带 Col 版的控件仅
      **144–152px**（低于 `MIN_FIELD_WIDTH=160`），auto-fit 拒绝分列是**设计语义不是回退**。已列为验收项。
      **R6 白名单复核**：原理由"`Currency`/`Price (CNY)` 不是缺陷"**对了一半**——
      `Currency` 已修（新增键 `priceCurrencyPlaceholder`，count 2 → 1）；`Price (CNY)` 是**语义问题**
      （该行选的是非基准币种，提示"按 CNY 填"有误导），**留给你裁决**。见 §13.3。
      新增 `ProductListPage.form.test.tsx`（**5 用例**）：`.perm.test.tsx` 的 7 个用例从不打开弹窗，
      故本页的 `FormModal`/`FormGrid`/子区块是零执行的。**证伪力已实测**（把栅格提前闭合、让 5 个字段
      落到栅格外 → 只有第 1 条红，其余 4 条仍绿）。
- [x] **T036 **[P] `customers/CustomerListPage.tsx` —— 外壳。**业务价值最高**（销售每天用）、
      `Form.Item` 最多（9 个）、唯一带自定义字段 + 第二个（纵向）转移弹窗的页面。测试耦合 **0/0/6**（中）。
      **三条约束已逐行坐实，不得违反**：① `:154` 与 `:245`（`not.toHaveClass`）的 `toHaveClass('ant-btn-primary')`
      ⇒ **不得把视图切换的 `<Space>`/`<Button>` 组换成 `Segmented`/`Radio.Group`**；
      ② `:136` 的 `getByText('Acme 科技')` 与 `getByRole(..., {name:/viewPool/})` **重复渲染即抛错**；
      ③ `:133` 的 `queryAllByRole('checkbox')` 按数量断言 ⇒ **不得改动 `rowSelection`**。
      **✅ 完成（1 文件 +2/−4）**：根 fragment → `div.page-stack`，并删掉 `<Space>` 与 `ProTable` 之间那个
      手搓的 `<div style={{ height: 16 }} />`。**位置差异如实记**：三条约束全落在**页首的 `<Space>`** 与被
      `rowSelection` 管着的复选框上，本次改动**一个都没碰**——`Space` 原样保留（未换成 `Segmented`）、
      `rowSelection` 原样保留、`viewColumns` 原样保留。
      **本页与 T030 不同、与 T032/T034 相同：净视觉变化为零。** T030 删掉的是一条"自身 16px + 手搓 spacer 16px"
      里的一层，故间距净减 16px（可归因）；本页那条手搓 spacer 本来就是**唯一的**那 16px，
      换成 `row-gap: 16px` 后间距**逐像素相同**，删掉的只是一个不再需要的 DOM 占位元素。
      另：`index.css:127-130` 的 `.page-container > div > .ant-card { margin-bottom: 16px }` 与 `.page-stack`
      的 `row-gap` **叠加**（§11.1 第 2 条）——本页 `ProTable` 也是最后一个 DOM 子元素
      （两个 `Modal` 经 portal 挂到 `body`），故只表现为页面底部多 16px，与 T030 同形，无害。
      门禁：typecheck / lint / `src/pages/customers/` **25 用例（5 文件）** 全绿。
- [x] **T037** `customers/CustomerListPage.tsx` —— `FormModal` + `FormGrid`。
      **不在此提交里抽 `ImportResultModal`**（会改动弹窗正文，属测试可见的 DOM 变更，另起提交）。
      **✅ 完成**：**两个** `Modal` → `FormModal`（创建 `size="md"`，宽度 640 与原值**逐像素相同**；
      转移 `size="sm"` = **520 → 480，−40px，本页唯一可归因的像素变化**，见 §14.3）、
      7 个 `<Col span={12}>` → `FormGrid`、标签宽 `100px` → `useFormMetrics()` 的 96/112、
      删 `saving` state、`onOk={() => void onSave()}` → `onSubmit={onSave}`（顺带修掉一处**改前就存在**
      的裸 rejection）。`CustomFieldFormItems` 与备注框**刻意留在栅格之外**（全宽项，纪律第 1 条），
      见 §14.4。
      **本页是四页里唯一真的修掉一个可用性缺陷的**：原 `<Row>` + 7 个 `<Col span={12}>`
      **写死两列且零断点**，375px 视口下每列仅约 140px（< 160），`FormGrid` 退成一列——逐档算术见 §14.2。
      另清掉转移弹窗的两笔债：R2（此前根本没设宽度）与 `confirmLoading`（`batchTransferCustomers` 无幂等键）。
      新增 `CustomerListPage.form.test.tsx`（**7 用例**）：两个既有测试文件**都不碰栅格、也不断言标签宽度**，
      故这一页的 `FormModal`/`FormGrid`/第二个弹窗此前是零执行的。**证伪力已实测两次**：
      ① 把备注 `Form.Item` 挪进一个新 `<FormGrid>` → 第 1、2 条红（`form-grid` 命中多个），其余 5 条绿；
      ② 创建弹窗 `size="md"` → `"sm"` → **只有第 1 条红**，报文 `expected '480px' to be '640px'`。
      两次探针均已还原，`PROBE` 残留 0，之后 6 文件 32 用例全绿。
      **门禁**：R2 `23 处/58 → 22 处/56`、R3 `97 处/99 → 90 处/92`（隔离测量，各减 7）、
      白名单 54 处不变、i18n 2888/2888（本页零新增键）。⚠️ §11.2 的 T031 读数与本次隔离测量**对不上**
      （+1 命中/−3 候选点，本批次提交解释不了），已记进 §14.5：**R3 绝对计数跨小节不可比**。
- [X] **T038** 视觉验收（**用户执行，SC-005**）：1920/1440/1024/768/**375** × 中英文，
      4 个页面的列表 + 弹窗 + 详情；并就 plan.md 末节的 **6 件事**给出裁决。
      → **已做**（2026-09-15 用户反馈「视觉验收通过」）。**验收粒度是用户自述，不是逐档复量的矩阵**
      ——本条记录不声称我逐档验过 1920/1440/1024/768/375 × 中英文，那是用户执行的那一半。
      6 件事的落地情况**逐条核实过代码**（不是照 plan 抄），结论见下；其中**只有第 4 项需要裁决**。
      ⚠️ 验收时点的一个边界：T053 的圆角统一（`bac6da6`）落在 P4 之后、本次验收**之前**，
      故「通过」涵盖的是 8/8/8 的圆角，不是改前的 8/10/12。

      | # | 决策 | 实测落地 |
      |---|---|---|
      | 1 | 全站字号 13 | ✅ `theme/index.ts:119`（种子）+ `index.css:65`（`--font-size-base`）+ `index.css:83-85`（`body`），三处一致 |
      | 2 | 统一标签宽度 中文 96 / 英文 112 | ✅ `useFormMetrics.ts:39,45`；`labelWidthFor` 已抽成纯函数且带语言分支（`:48-50`），hook 只转调 |
      | 3 | 保留 `layout="horizontal"` | ✅ 4 个 FormModal 页均为横排 + `labelCol={{ flex: metrics.labelWidth }}`。**订正一句口径**：全库仍有 50 处 `layout="vertical"`（筛选表单、窄弹窗、页级表单），"保留横向"指的是**主表单**，不是全站没有纵向表单 |
      | 4 | `FormModal` 自定义 footer + Enter | ⚠️ **拆成两半，只采纳后半**——见下方订正 E |
      | 5 | R2/R3 先宽后紧 | ✅ `check-ui.mjs:624-625`（`strict: true`）+ `:666-670`（默认档 push 进 warnings 后 `continue`） |
      | 6 | 内容区 `maxWidth` 封顶 | ⚠️ **变量从未引入**——见下方订正 E |

      第 6 项补一句口径：plan 的推荐是「先 `none`，P1 只引入变量、零视觉变化」。实测全 frontend
      搜 `content-max-width` / `contentMaxWidth` / `CONTENT_MAX_WIDTH` **零命中**，`.page-container`
      只有 `width: 100%`（`index.css:102-105`）⇒ **变量没建，但功能上恰好等于推荐值 `none`**
      （没有任何容器封顶）。**不补建该变量**：本仓已有一个零消费者的 `--radius-xl`（见「附带发现」#10），
      再造一个是同一个味道。「没有理由的 token 不写」是这里的既有纪律。

### 订正 E：第 4 项决策**只采纳了一半**（Enter 采纳、自定义 footer 不采纳），并订正第 6 项

**（一）第 4 项的裁决与落地**（2026-09-15，用户拍板）

plan 的原话是「`FormModal` 用自定义 footer + Enter 提交，取代 antd 默认的 `Modal.onOk` 脚注」，
推荐「采用」。**验收时只采纳了 Enter 那半句**，理由是原判词把两件事捆在一起、而它们是可分的：

- 那一项自称的价值是「把 `validateFields()` 从 ~58 个页面里删掉」。**这件事 `onSubmit` 已经做到了**
  （调用方不再自己 `validateFields()`，loading 与「异常不吞」也由组件兜住）——自定义 footer
  **不是这项收益的来源**。
- 代价却是实的：OK 按钮不再是 antd 默认脚注，而全站 9 个自己写了 `footer=` 的弹窗
  （contacts / leads / departments / marketing / portal / tasks / open ×2 / map）
  **用的都是裸 `<Modal>`、根本不经过 `FormModal`**。即：为一个不带来收益的改动去动 9 个无关页面。
- `footer` 的透传口**保留**，将来改主意不必再动契约。

落地在 `FormModal.tsx` + 新增的 `formModalEnter.ts`。**新增 8 条用例**（`FormModal.test.tsx`
从 **9 → 17**；落地时一度记作「10 → 17 = 新增 7」，是**把左端数错了**——`git show HEAD:`
那份声明数实测为 9 条 `it`，全量因此是 378 + 8 = **386**）。其中「不提交」的三条比
「能提交」的那一条更要紧——只测「Enter 能提交」，会把一个「到处误提交」的实现判成绿的。

**（二）两处此前不知道的事实，都是被实测逼出来的**

1. **`<Modal onKeyDown={...}>` 静默失效。** antd 把未知 prop 收进 `restProps` 交给 rc-dialog 的
   `Dialog`（`antd/es/modal/Modal.js:126`），`Dialog` 再 `{...props}` 传给 `Content`，而
   `Content` **只解构自己认识的那些**再交给 `Panel`，`Panel` 也只写死属性
   （`rc-dialog/es/Dialog/Content/Panel.js:116-139`）。于是 `onKeyDown` 一层层被丢掉——
   **不报错、不警告、DOM 上也没有**。故锚点改为一层自己的包裹 `<div onKeyDown>`。
   加它之前核对过本库 CSS 对 `.ant-modal-body` 子级无选择器依赖（`index.css` 里与弹窗相关的
   只有一条 `.ant-modal .ant-row .ant-col`），故这层包裹是安全的。

2. **我写的第一版实现里有一条死分支。** 原本除了「只认 `INPUT`」之外，另写了一条
   `if (el.tagName === 'TEXTAREA') return false`，注释还写着「误提交会让用户根本敲不出第二行」。
   **定向破坏证明它恒不可达**——见下表 ①。已删除该行，理由并入末行注释：
   `TEXTAREA !== 'INPUT'` 本来就兜住了。**留着它比删掉它更危险**：一行不起作用的代码配一句
   "它很要紧"的注释，会让下一个动这块的人以为改了它才有事。

**（三）定向破坏留痕**（逐个做、逐个逐字节还原；还原后已核对无 `BROKEN` 残留）

| 破坏 | 结果 | 结论 |
|---|---|---|
| ① `tagName === 'TEXTAREA'` 改成恒不成立 | **17 条全绿** | 那条守卫是**死分支**（末行已兜住）→ **据此改了实现** |
| ② `.ant-select, .ant-picker` 改成恒不匹配 | **红 3 条** | 正是 DatePicker / Select / 判据表三条，该守卫承重 |
| ③ 撤掉包裹 `div`、`onKeyDown` 挂回 `<Modal>` | **红 2 条**，报 `got 0 times` | `onKeyDown` 在 `<Modal>` 上**一次都不触发**，把上面第（二）1 条从读源码的推断变成实测 |
| ④ 末行 `tagName === 'INPUT'` 改成恒真 | **红 2 条** | 「TextArea 不提交」**不是空过**，它由末行单独承重 |

**（四）一处不是设计选择、而是被门禁逼出来的结构**

`shouldSubmitOnEnter` 从 `FormModal.tsx` 拆到 `formModalEnter.ts`，**不是风格偏好**：
`eslint` 的 `react-refresh/only-export-components` 会因「组件文件里导出非组件」报 warning，
而本仓 lint 门禁要求**零 warning 且不许 `eslint-disable`**。同目录的 `formModalSize.ts`
是同一个原因拆出去的先例。

**（五）订正第 6 项**

plan 推荐「先 `none`，P1 只引入变量、零视觉变化」。实测该变量**从未引入**（见 T038 表格下的口径），
即「只引入变量」这半句没做。功能上无碍（没有任何容器封顶 = `none`），故**不补建**——
理由同 T038 条目里那段。

---

## P3：铺开（样板验收通过后）

> **按表单形态分批，不按业务模块分批**——这样每批是一次机械变换 + 一套验证。

- [ ] **T040** 3.1 ~~其余 **3 个无宽度弹窗**（`InvoiceListPage` 与 `TagListPage` 已在 P2 收掉）：
      `announcements/AnnouncementPage.tsx`、`sla/SlaCalendarPage.tsx`、`visits/VisitListPage.tsx`。
      完成后把 **R2 从 warn 翻成 error** 并删掉已还清的条目。~~
      → **前提不成立，已按实测重切**（2026-09-15，用户裁决）。原判词保留在上（删除线），逐条对：

      | 原文点名的文件 | 实测 |
      |---|---|
      | `announcements/AnnouncementPage.tsx` | 它的 Modal 在 `:195` **已有 `width={640}`**——**根本不在 R2 名单里** |
      | `sla/SlaCalendarPage.tsx` | **没有 Modal**。它是页级 `<Form layout="vertical" style={{ maxWidth: 560 }}>`，不属 R2（R2 判据是「`<Modal>` 区域内出现真实 `<Form` 标签」） |
      | `visits/VisitListPage.tsx` | ✅ 真在名单里，且是 **2 处**（`:268`、`:294`） |

      即：原文说的「3 个」里**只有 1 个成立**——与 T052/T053 是同一类错误（**判词先于实测**）。

      **重切为（用户选定）：本项 = 清完 R2 全量 22 处 / 16 个文件，独立一批、一次提交。**
      出口判据同时订正——原文「删掉已还清的条目」**够不着**：R2 的 `allowed` 是 `null`
      （**没有白名单可销**），只有 22 处**全部**清零才翻得动 error。

      | 文件 | 处数 |
      |---|---|
      | `users/UserManagementPage.tsx` | 4 |
      | `visits/VisitListPage.tsx` | 2 |
      | `open/OpenPlatformPage.tsx` | 2 |
      | `contracts/ContractDetailPage.tsx` | 2 |
      | 其余 12 个文件（含 2 个组件、3 个详情页） | 各 1 |

      全量取自 `node scripts/check-ui.mjs --strict` 的 R2 段，可复跑复算。
      这 22 处横跨**组件**（`FollowUpTimeline`、`LeadConvertModal`）、**列表页**、**详情页**三类，
      故**不适合**按页面族拆进 3.2–3.5——同一个文件会被碰两次。修法机械：每处补 `width`
      或改用 `FormModal`（四档 `sm/md/lg/xl`）。
- [ ] **T041** 3.2 其余 `layout="vertical"` 的表单（**43 个**）—— **密度收益最大的一批**
      （单列堆叠 → 两列栅格，表单高度直接减半）。
      ⚠️ 注意不要把"43 个纵向**表单**"与"58 个承载表单的**弹窗**"混为一谈（§2.2 与 §2.4 是两个分母）。
- [ ] **T042** 3.3 其余横向表单（**22 个**，全部已设 `labelCol`；把 `labelCol` 定宽改用 `useFormMetrics`）。
- [ ] **T043** 3.4 **13 个页面级表单**。
- [ ] **T044** 3.5 详情页（077 系 4 页 vs 传统系 `Descriptions` 11 个文件两套版式）。
- [ ] **T045** **R3 从 `--strict` 翻成 error**（96 处销账完毕后），并删除 P2/P3 期间已还清的全部白名单条目。

---

## P4：退役（与本批次相关的对抗性 CSS）

> **执行记录：2026-09-14**，四次提交 `3e5f054`（T050）/ `042de33`（T051）/ `db71923`（T052）/ `11f04d2`（T054）。
> 原三条退役任务里，**两条的判词经实测不成立**，订正见下方 A / B / C——原判词一律保留不删。
> `src/index.css` 的 `!important`：**实体声明 22 → 10**（T050 −10、T051 −2、T052 ±0）；
> 若按 `measure-ui-baseline.mjs` 的口径（**不剥注释**，本次新增 3 处注释提及）则为 **23 → 14**。
> 探针一律 untracked、用完即删；每个步骤各自的改前/改后读数都写进了对应提交信息。

- [X] **T050** 删 `index.css` 的 `.ant-btn-primary` `!important`（**单独一次提交**，让视觉差异可归因）。
      → **已做**（`3e5f054`）。判词「`.ant-btn-primary`」范围偏窄：实际退役的是**按钮家族 10 处**
      `!important`（`.ant-btn-primary` 4 处 / `.ant-btn-dangerous` 2 处 / `.ant-btn-dangerous.ant-btn-primary` 4 处），
      并**先补主题 token 再删**，理由见订正 A。
- [X] **T051** 删 `.ant-menu-item-selected` `!important`（同上）。
      → **已做**（`042de33`）。删 2 处 `!important` + 同族一条**空转**的 `::after { right: 0 }`。
      读数：选中项底色 `rgb(238,242,255)` → `rgb(240,243,255)`（通道差 ≤2，属预期内极低可见性差异），
      字色与字重**逐字未变**。
- [X] **T052** 删 `.ant-pagination-item-active` `!important`（同上）。
      → **订正 B：该段一处 `!important` 都没有**，判词前提不成立；经实测改为「只删其中 4 条已死的规则」（`db71923`）。
- [X] **T053** 清扫 **66 处** `borderRadius: 10` 字面量（改用 token）。
      → **执行记录：前提两次被推翻，落点最终由用户裁决，与订正 C 的处置不同。**
      订正 C 判「跳过、另立遗留项」之后，用户裁决**「T053 统一」**，并选定目标值 **8**（= 主题种子，
      即卡片与控件同圆角）。故本项**在 088 内执行完毕**，不再是遗留项。
      已做（`931d828` 单源化 + `bac6da6` 清扫字面量）。扫描器复跑：
      `borderRadius: 10` **62 处 / 53 文件 → 0 处 / 0 文件**。详见订正 D。
      （原文「66 处」保留不删——它是 `0fcdc14` 时的读数，其后的差额已在订正 C 逐条对过。）
- [X] **T054** 删 `src/components/ContactsCard.tsx`（真孤儿，0 引用）并删掉 R7 白名单条目。
      → **已做**（`11f04d2`）。白名单 `54 → 53`（R7 由 1 → 0），符合 SC-006 的单调收缩。

### 订正 A：T050 的 hover 四处**不是**纯冗余，是**必需**的——故先补 token 再删

原判词基于「`index.css` 文档序在后，同特异性时稳赢 antd」这一条实测事实。该事实**对基态成立、
对 hover 不成立**：antd 的 hover 选择器是 `&:not(:disabled):not(.ant-btn-disabled):hover`，
特异性 **(0,4,0)**，压过 `index.css` 的 `.ant-btn-primary:hover` **(0,2,0)**。故那四条 `!important`
今天是真的在起作用，裸删会让 hover 由 `#4f46e5` 变成 antd 派生的 `#9197ff`（**更浅**）。

做法：在 `theme/index.ts` 补 `palette.errorHover` 与 `components.Button.{colorPrimaryHover,colorErrorHover}`
（按组件收窄，不写全局别名 token——后者会顺带改掉链接 hover、聚焦环等一大片），**再**删那 10 处。
判据：删后 hover 仍为 `rgb(79,70,229)` ⇒ 剩下的唯一来源已是 antd ⇒ 组件级 token **确实被采纳**
（plan 的三级降级阶梯第 1 级成立，无需降级）。

唯一一条计算值变化是 `.ant-btn-primary` 的 `border-color`：`rgb(99,102,241)` → `transparent`
（antd 的主按钮自带 `border: 1px solid transparent`，被删的那条写的是与底色同值）。
**没有靠推断收尾**，做了同页同元素的像素 A/B：两态差异像素 **174 个、通道差 >16 的 0 个**，
边框环采样逐点相同 ⇒ 圆角抗锯齿噪声，可感知差异为零。

### 订正 B：T052 的判词两次都不成立，最终按实测裁到 1 条

判词第一版「删 `.ant-pagination-item-active` `!important`」——该段**一处 `!important` 都没有**。
第二版据此改判「整段死代码，可整段删」——**实测也不成立**：段内五条规则里 `.ant-pagination`
的字号是**活的且可见**（12px → 13px；antd 经 `resetComponent` 给根节点写 `token.fontSize` = 13px，
此处同特异性而文档序在后）。故该条**保留**，它是自定的视觉选择而非对抗性覆盖。

其余 4 条规则的**全部 7 条声明**实测计算值无变化，**已删**。它们的目标选择器一律被 antd 更高
特异性的规则压住（`.ant-pagination .ant-pagination-item` 为 (0,2,0)，非选中项 `:hover` 为 (0,4,0)），
**从未生效过**——是被覆盖的一方，不是覆盖者。其中 `-item:hover { color }` 确有计算值变化
（Indigo → `rgba(0,0,0,0.88)`），但页码数字在 `<a>` 里、`<a>` 自带 `color: colorText`，
像素比对**零差异**，故一并删除。

**连带订正 `theme/index.ts` 的错误记载**：该文件第 15 行的表格把 `.ant-pagination-item-active`
列进「跟着 Indigo（**被 CSS 覆盖到了**）」。实测其边框与字色一直是 antd 自己的 `colorPrimary`
派生的（P1 接入主题后才成 Indigo，此前是 antd 蓝），它本属表格**右**栏。已移正并留痕。

### 订正 C：T053 的前提**不成立**，本项跳过、另立遗留项

原判词：那 10 是主题派生的 `borderRadiusLG` 的重复，删掉由主题接管。**实测否证**——同页自然
对照组（`/data-retention` 与 `/departments`，类名 `ant-card ant-card-bordered` 与父元素
`page-fade` **完全相同**，只差一个内联属性）给出 **12px 与 10px**；而源码里**根本没有**该内联的
`/quotas` 表格卡算出 **8px**，其余各页（有内联）算出 10px。即那些字面量是**唯一取值来源**，
删掉会把 ProTable 卡片 10 → 8、antd Card 10 → 12，**是可见变更而非去重**。

现状是页面上有三种卡片圆角并存：**8**（ProTable/ProCard 默认 = P1 的主题种子）、
**10**（被字面量钉死的绝大多数）、**12**（`.ant-card` 全局规则、`.stat-card`、`.ant-table`）。
**统一它是另一个决定，属遗留项**，本规格不做。证据（对照组读数）已写进 `theme/index.ts` 的
`borderRadius` 注释，防止下一个人再提一次。

> ⚠️ **后续（订正 D）**：本段判「属遗留项、本规格不做」已被用户裁决推翻——T053 已在 088 内执行完毕。
> 本段原文保留，不改写；其事实部分（字面量是唯一取值来源、删掉是可见变更）在订正 D 里被进一步
> **坐实并定位到具体 token**，结论无误，只是处置改了。

计数订正：本文 T053 原文写的「66 处」是 `0fcdc14` 时的读数。`measure-ui-baseline.mjs` 现报
**62 处 / 53 文件**，即**代码字面量本身**。

差额来源逐条可对：`0b810b0` 合法 −3（InvoiceListPage 收掉三个手搓统计块）；`11f04d2` 随孤儿组件 −1
（`ContactsCard.tsx:151` 带有一处）；再把 `theme/index.ts` 那条**自指注释**排除掉，又 −1。
最后这一条值得单说：该脚本的口径是 `/borderRadius:\s*10\b/` 的**裸匹配、不剥注释**，而那条注释里
原样写着 `borderRadius: 10`——于是注释把自己也数了进去，**口径长期虚高 1 处**。现已把该注释改成
不会命中正则的写法（写作「圆角字面量取 10」），口径与代码实况对齐：**62 处 / 53 文件可复跑复算**。

### 订正 D：T053 的前提与「跳过」两度改变——最终执行，目标值由用户裁决为 8

**第三版判词（本段）取代的是订正 C 的处置，不是它的事实。**订正 C 的实测事实经复核**全部成立**，
并补上了它当时缺的一环：**究竟是哪个 token 在供值**。

用 CDP 的 `CSS.getMatchedStylesForNode` 取了**全量竞争规则**（不只取胜者），八个面族的真源如下：

| 面 | 供值的规则 | 供值的 token | 改前实况 |
|---|---|---|---|
| `.ant-pro-card` | `@ant-design/pro-card` 的 `Card/style.js:23` → `borderRadius: token.borderRadius` | **全局种子 `borderRadius`（8）** | 8px（无字面量）/ **10px**（34 处 `cardProps`） |
| `.ant-card` | 本仓 `index.css` 的 `.ant-card { var(--radius-lg) }` | `--radius-lg`（12） | 12px / **10px**（~13 处内联） |
| `.ant-table` | antd 的 `:where(.css-x).ant-table-wrapper .ant-table` = **(0,2,0)** | 由种子派生的 `borderRadiusLG`（10） | **10px 10px 0 0** |
| `.stat-card` / `.kpi-card` / `.dashboard-card` / `.ai-suggestion-card` | 本仓 CSS | `--radius-lg`（12）/ 硬编码 12 | 12px |

**三条此前没有的发现（都是订正）**：

1. **ProCard 读的是种子 `borderRadius`，不是 `borderRadiusLG`**。这解释了订正 C 的对照读数：
   `/quotas`（QuotaListPage，源码里**没有**该内联）算出 8；`/orders`、`/users` 算出 10。
   也说明「删除字面量 = 回到 8」而非回到 10。顺带订正一处**我自己先前的误认**：
   订正 C 举的对照组是 `/data-retention` 与 `/departments`，但 `/quotas` 当时被我读成
   `QuoteListPage`（实为 `QuotaListPage`）——**结论未变，举例的归属说错了**，在此订正。
2. **`index.css` 的 `.ant-table { border-radius: var(--radius-lg) }` 是一句死声明**：它 (0,1,0)，
   恒输给 antd 那条 (0,2,0)，**从来没有生效过**。同规则里的 `overflow: hidden` 无竞争者，是活的。
3. **`.ant-card { border-radius: var(--radius-lg) }` 是第二处真源**：它靠「同特异性 (0,1,0) +
   `prepend:'queue'` 使 antd 在前」赢，圆角其实一直由它而非主题决定。

**做法（两步提交，差异可归因）**：

- `931d828`（只动源，不碰字面量）：`--radius-lg` 12 → 8；主题补 **`borderRadiusLG: 8`**（全局，
  不逐组件收窄——本项要的就是「大圆角面一起走」）；删掉 `.ant-card` 与 `.ant-table` 各自那条
  圆角声明（前者重复、后者是死的）；`.dashboard-card`/`.kpi-card`/`.ai-suggestion-card` 的硬编码
  12 → `var(--radius-lg)`。改后实测：未钉死的面**全部落到 8**，钉死的仍是 10（可归因）。
- `bac6da6`：清扫字面量（62 处 → 0 处 / 0 文件），58 个文件。

**三处不是删、而是改**（删了会变直角或机制上不允许），逐条给理由：

| 位置 | 为什么不能直接删 | 改成 |
|---|---|---|
| `visits/VisitListPage.tsx:228,233,238` | 是**裸 `<div>`**，不是 antd 组件，删掉变**直角** | `var(--radius-lg)`，实测内联样式里能解析，三处均 8px |
| `dataVision/components/HealthChart.tsx:51` | **ECharts `itemStyle`**，画在 canvas 上，**读不到 CSS 变量** | 数值 8，原位注明 |
| `map/UsageMapPage.tsx:310` | 是**按钮**（快捷入口），不属卡片族；`067` 的 `plan.md:148` 明文写过 10 | `var(--radius-md)`（全站按钮的 8），并订正 067 原文（见其文件内的订正块） |

**一并处理**：卡片本体上的 15 处内联 `borderRadius: 12`（都是 antd `<Card>`，删掉回归主题）；
`InstallPrompt` 浮层与 `GlowBorder` 深色自绘的 4 处内联 12（裸 div，改 `var(--radius-lg)`）。
`--radius-lg` 由 12 改 8 还**顺带收编了 19 处已经写成 `var(--radius-lg)` 的详情页卡片**——
它们此前是 12、列表页是 10，同属本项要消掉的那种不一致。

**明确不动（写成遗留，不夹带）**：

| 遗留 | 理由 |
|---|---|
| `/stats` 的 `.kpi-icon-wrapper`（48×48）与 `.ai-icon-pulse`（42×42）内联 12 | **图标底**，不是卡片面，不属本项范围 |
| 内联的 `borderRadius: 8`（14 处）/ `6`（6 处）/ `4`（3 处） | 与新值同族或属别的族，**无视觉变化**，属「字面量与 token 同值的冗余表达」，另一件事 |
| `--radius-xl: 16px` 目前**零消费者**，而 `.kpi-icon-wrapper` 的 CSS 硬编码同一个 16 | 附带发现：该 CSS 又被该元素的内联 12 压死（**又一处死声明**），未动 |

**门禁**：typecheck / lint / i18n:check / menu:check / perms:check / ui:check 全绿；
`Test Files 83 passed (83)` / `Tests 378 passed (378)`，与基线一致（不减少）。

### 附带发现（P4）

| # | 发现 | 处置 |
|---|---|---|
| 8 | 删 `ContactsCard.tsx` 使 `ui:check` 的 **R2 候选点由 56 → 55**（该文件含一处候选点）。不是新违规，`--strict` 口径下待还数 22 未变 | 已在 T054 的提交信息中记录 |
| 9 | 探针自身出过两次错、均被其**自证断言**拦下：① `transition: all` 未走完就读计算值+截图，读到过渡起点；② hover 组拿基态的 `#6366f1` 当模拟旧值，而那条规则原本写的是 `#4f46e5` | 两次都已在探针注释里留痕；方法论教训：**A/B 的参考值必须逐态取，且带过渡的属性必须先等它走完** |
| 10 | T053 途中又发现**一处死声明**（本项范围内已处理两处：`.ant-card` 是第二真源、`.ant-table` 的 `border-radius` 恒输给 antd 的 (0,2,0)）。**范围外**这一处：`.kpi-icon-wrapper` 的 CSS 硬编码 `border-radius: 16px`，被该元素自己的内联 12 压死——而 CSS 的 16 恰好等于 `--radius-xl`，该变量目前**零消费者**。同族的 `.ai-icon-pulse`（42×42）也吃内联 12 | **未动**（图标底不属本项范围，见订正 D 的「明确不动」表）。留作下一批 `index.css` 全量清理的输入：这类「被内联压死的 CSS 声明」在本文件里可能不止一处，且**没有任何门禁能发现** |
| 11 | **计数脚本的 `!important` 口径也虚高，成因与「自指注释」同类**。`measure-ui-baseline.mjs:200` 是 `read('index.css').match(/!important/g).length`——**裸匹配、不剥注释**。但 T050/T051 的订正**必须**在注释里写出 `!important` 才能说明「删了什么、为什么」⇒ 收工后脚本报 **14**，而**实体声明只有 10**，与上文 :270 记的「22 → 10」**不矛盾**（那句是实体口径，且是对的）。差额逐条可对，全在注释里：`index.css:872`、`:922`、`:949`、`:985` | **未改脚本**（改计数口径＝改判据，须单独一步做并重新验证，不在 T053 的收尾里夹带）。此处**订正的是口径，不是结论**：实体 10、脚本 14，两者都对，差 4 全是注释文字。**下一个用这个数字的人，请先确认自己问的是哪一问** |


---

## 附带发现（本规格途中发现，**不在本规格范围内修**）

| # | 发现 | 处置 |
|---|---|---|
| 1 | **规则 2 的真实基数是 25 处 / 19 文件，不是 plan 初稿的 23** —— 23 是**只看 `src/pages/`** 的口径，规则扫全部产品 tsx，多出 `components/FollowUpTimeline.tsx:141` 与 `components/LeadConvertModal.tsx:43` | 两个数都对，量的是不同范围；已记入 plan.md 与 research.md §8 |
| 2 | **规则 6 的 11 处构成与 research.md §2.9 不同**：§2.9 把 `title=` 也计入（`LoginPage` 记 2 处），实现只扫 `placeholder`/`aria-label`（`LoginPage` 记 1 处），差额由 `ScheduledExportCreatePage.tsx:115` 的 JSON 示例补上。**总数巧合相同，构成不同**；§2.9 的"6 处该翻译"应改为 **2 处**（两个 `aria-label`） | 已记入 research.md §9 |
| 3 | `vite.config.ts:45-56` 的覆盖率快照**已过期**（写的是"22 文件 / 96 用例"，实际已 72→76 文件），且 `specs/083-engineering-consolidation/data-model.md §4` 自己声明了同步义务 | **已刷新**（2026-09-15，由并行会话落地，**不是我做的**）：`42d6b07`「chore: 刷新前端覆盖率实测快照（阈值一律未动）」。追加 09-13 实测 83 文件 / statements·lines 68.55 / branches 73.68 / functions 36.58，连跑三次逐位相同；**阈值四项均未动**（符合 084 T037「不得下调」）。同批把 `data-model.md §4` 按该文件自陈的同步义务一并更新。另记一条**实测否决**：拟议给该文件加 `maxWorkers` ——空转机器上对拍后否决（默认池 3/3 全绿且读数逐位相同、141–146s；限 4 worker 慢一倍且 branches 反抖 0.01pp） |
| 4 | `.ai-card` **不是死代码——该选择器根本不存在**（`index.css` 里只有 `.ai-suggestion-card` / `.ai-icon-pulse`） | 已订正（research.md §4.1）；plan 初稿的「非目标」清单已删去该条 |
| 5 | `Descriptions layout="horizontal"` 的行号有两个口径：plan 引 `layout=` 所在行（317/357/217/299），脚本引**标签起始行**（313/353/213/295），差 4 行 | **统一用标签起始行**（唯一），已订正（research.md §4.3） |
| 6 | **`InvoiceListPage` 首屏从不拉统计**：`loadStats()` 只被 `reload()` 调用，而 `reload()` 只被 `onCreate`/`onVoid` 调用 ⇒ 首次进入页面时三个统计卡片恒为 `0% / 0.00 / 0.00`，只有开过票或作废过一张才变真值。换 `StatCard` **之前**即如此（同一个 `stats` state），与 T031 无关，但验收时极易被误读成"`StatCard` 把数字改坏了" | **已修**（T031 之后的第三次提交）：加挂载期 `useEffect`（照本仓 `useCallback`+`useEffect([load])` 惯例，避免 `exhaustive-deps` 警告）；`form.test` 首条断言从 `0%`/两个 `0.00` **翻成真实值**，且**实测过它真会红**（临时禁用 effect → 该用例失败、其余 4 条仍绿）。见 research.md §11.4 |
| 7 | **R3 的 96 处里至少有一处不能用 `FormGrid` 修**：`TagListPage.tsx:141` 的 `<Col key={c} span={2}>` 是颜色选择器的 **10 个色块**（全宽项），而 R3 的修复建议文本是"换成 `FormGrid` 或至少补断点"——对这一处**前半句是错的**（全宽项本来就该在栅格之外）。它确实有真实缺陷（320px 下每个色块约 20px），但正确修法是换成 `flex-wrap` 的色块行，属**结构变更** | **记给 T045**：R3 铺开时**不得机械替换**；本页在 T033 里保持原样，仍留在 R3 名单上（R3 计数 96 未变，是预期状态）。见 research.md §12.5 |
