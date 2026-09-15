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
      （⚠️ **此处是 T020 当时的快照，不是现状**：R2 已于 T040 毕业成默认档 error，见该条。）
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
      | 5 | R2/R3 先宽后紧 | ✅ `check-ui.mjs` 的 `ruleDefs`（`strict: true`）+ 默认档 push 进 warnings 后 `continue`）。**⚠️ 已随 T040 订正**：R2 于 2026-09-15 毕业成默认档 error（`strict: false`、`allowed` 仍 `null`），**只剩 R3 还在 `--strict` 后面**——见 T040 的「出口判据的机制订正」 |
      | 6 | 内容区 `maxWidth` 封顶 | ⚠️ **变量从未引入**——见下方订正 E |

      第 6 项补一句口径：plan 的推荐是「先 `none`，P1 只引入变量、零视觉变化」。实测全 frontend
      搜 `content-max-width` / `contentMaxWidth` / `CONTENT_MAX_WIDTH` **零命中**，`.page-container`
      只有 `width: 100%`（`index.css:102-105`）⇒ **变量没建，但功能上恰好等于推荐值 `none`**
      （没有任何容器封顶）。**不补建该变量**：本仓已有一个零消费者的 `--radius-xl`（见「附带发现」#10），
      再造一个是同一个味道。「没有理由的 token 不写」是这里的既有纪律。

      ✅ **补记（2026-09-15，用户「签字」）**：用户当日**第二次**行使 SC-005，覆盖的是 **T040–T044** 五批
      在报告里逐条披露的可见变化（P3 各批的判据④此前一律记「尚未执行」，至此一并结清）。
      **本条的验收粒度口径照旧适用**——用户自述，**不是**逐档复量的矩阵；当时**没有**再走一遍
      1920/1440/1024/768/375 × 中英文，不得据此声称走过。**扩用说明**（本条原文点名 4 个样板页，
      而 T040–T044 改的是别的页面）与**签字不含的三件事**见 `spec.md` 的 SC-005「验收记录」。

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

- [x] **T040** ✅ **已完成**（2026-09-15，R2 22 处全部销账并翻 error） 3.1 ~~其余 **3 个无宽度弹窗**（`InvoiceListPage` 与 `TagListPage` 已在 P2 收掉）：
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

      **执行结果**（2026-09-15，一次提交）：**22 处 → 0 处**，候选点未变（本项只加属性、不增删弹窗）。
      16 个文件、**22 处**新增 `width`，但**不是**「各一处」：`UserManagementPage.tsx` **4** 处、
      `ContractDetailPage.tsx` / `OpenPlatformPage.tsx` / `VisitListPage.tsx` 各 **2** 处、
      其余 **12** 个各 1 处（4 + 2×3 + 12 = 22，可复算）。
      判据 `git diff -U0 -- src | grep -c '^+.*width={'` = **22**；
      `git diff --numstat -- src` = **16 行**，`--shortstat` 为 **24 insertions / 4 deletions**——
      插入数比 22 多出的 2 行是 `FollowUpTimeline.tsx` 的 `Modal` 标签因新属性超行宽而折行，
      删除数 4 行里 3 行是三个单行标签（`OpenPlatformPage` 2、`FieldPermissionPage` 1）的就地重写、1 行是同一处折行。

      ⚠️ **读数出处的订正**：本行初稿写「`ui:check` 读数为『R2：**0 处待还**（55 个候选点）』」——
      **那个读数属于翻档前的中间态，不是最终态**。R2 的 `strict` 仍为 `true` 时，它会在**提示区**打印这一行；
      已逐字复现：拿 `git show HEAD:frontend/scripts/check-ui.mjs`（翻档前那份）跑当前 `src`，得到
      「R2（承载表单的 Modal 必须显式定宽（--strict））：0 处待还（55 个候选点）。加 --strict 会把它当失败。」
      `strict` 改成 `false` 后，R2 改走 `allowed === null` 分支，**零命中时不打印任何一行**——
      这正是「毕业」的可见后果。故**最终态的判据是「退出码 0 + R2 在提示区与问题区都不出现」**，
      不是某一行读数；`MIN_CANDIDATES.R2 = 1` 的自检又保证「不出现」不是扫描器空转。
      **`0 处待还 / 55 个候选点` 两个数经复核成立**，但出处是**探针**而非门禁：把 `scripts/check-ui.mjs`
      复制成一份插了 `console.error` 的临时副本（dot 前缀、跑完即删、未进仓库），走同一条 `rule.run()` 路径，
      实测 `R1 32/32 · R4 5/148 · R5 6/149 · R6 10/10 · R7 0/21 · R2 0/55 · R3 90/92`。
      候选点 **55** 与 T054 的记录（删 `ContactsCard.tsx` 后 56 → 55）对得上。

      ⚠️ **出口判据与 `research.md` §11.2 的一处冲突，已就地订正**：该节原写
      「P3 每批的出口判据不该用『R2 剩几处』，而该用『**哪些文件还在白名单里**』」。
      后半句对 R2 **够不着**——R2 的 `allowed` 是 `null`，**没有白名单**，没有条目可做判据；
      前半句的担忧（数字因迁移而掉 ≠ 真还债）是对的，但**可当场分辨**：

      | 读数 | 真还债（本项实际情形） | 迁移造成的假掉 |
      |---|---|---|
      | 待还 | 22 → **0** | 也会降 |
      | **候选点** | 55 → **55（不变）** | **同时降**（`FormModal` 不含 `<Modal` 子串 ⇒ 退出分母） |

      ⇒ 本项的出口判据因此收紧为「**待还归零 _且_ 候选点不变**」，
      才能断言 22 处是**在原地**修掉的、没有一处靠换控件悄悄离开分母。
      （本项走 `width`、不换控件，故两个数一降一平——正是这一行。）订正已回写 `research.md` §11.2。

      **修法订正：采用 `width`（四档裸数值），不采用 `FormModal`。** 逐条理由：
      1. R2 的出口是「显式定宽」，`width` 与 `FormModal` 都满足；但改用 `FormModal` 会**顺带**改掉
         5 项行为（`cancelText` 由 antd 的 "Cancel" 变「取消」、`confirmLoading` 接管、
         **`destroyOnClose` 无条件打开**、**新增 Enter 提交**、孩子外面多包一层 `<div>`）。
         那是 3.2/3.3 的活，塞进「清 R2」这一批就会让 22 个弹窗的行为变更**没有各自的验证**。
      2. 写**裸数值而非 `FORM_MODAL_WIDTHS.sm`**：`components/ui/index.ts` 明确**不导出**档位表
         （原话：「导出会诱使页面自己拼宽度」）。这与既有 38 处裸 `width=` 的写法一致。
      3. 档位按**内容**取，不按偏好：`Form.Item` **1–2 个 → `sm`(480)**（13 处），
         **3 个及以上 → `md`(640)**（9 处）。依据是 `formModalSize.ts` 的列数表
         （`sm`→1 列、`md`→2 列），且 640 是既有 38 处显式宽度里的**众数**（11 次）与 `FormModal` 的默认档。
         ⇒ 将来换成 `FormModal` 时是 **480↔sm、640↔md 的一一对应**，不必重新决策。

      ⚠️ **本项产生可见变化，必须在 SC-005 视觉验收里终判**：这 22 处此前吃 antd 默认 **520**，
      现在 **13 处变窄到 480（−40）**、**9 处变宽到 640（+120）**。四档里**没有 520**，
      所以「像素不动」不是一个可选项——要么窄一点、要么宽一点。本项只保证差异被量出并写明，
      **不保证零变化**。

      ⚠️ **一处开工前不知道、会改到 T041 scope 的实测**（逐弹窗核对，不是按文件猜）：
      22 个 R2 弹窗里 **20 个**的表单是 `layout="vertical"`，另 2 个
      （`marketing/EmailCampaignPage.tsx:237`、`visits/VisitListPage.tsx:268`）不是；
      而那 16 个文件合计含 **22 处** `layout="vertical"`（另 2 处在弹窗之外）。
      即 **T041（3.2「43 个纵向表单」）与 T040 大面积重叠**，不是完全重合。
      两者**正交**（R2 管 `<Modal>` 上的宽度、3.2 管表单**内部**的栅格），所以 T040 不是白做，
      但 **T041 开工前必须重数自己的分母**——原文的「43」与全库实测的 **51 处 / 44 文件**
      差 8，两个数口径不同（「表单」vs「声明」），**先数再动**，不得沿用 43。

      **出口判据的机制订正**（原文与 `check-ui.mjs` 的文档都够不着，已就地订正）：
      该脚本 `<h3>先宽后紧</h3>` 原写「计数归零的那一天，`pnpm ui:check --strict` 就是新的门禁」——
      **这条走不通**：整档切换会连带把 **R3 的 90 处**（同日的 T046 修缺陷后订正为 **89**）也变成失败，而 R3 是 3.2/3.3 的活。
      故改为**逐条毕业**：R2 的 `strict` 由 `true` 改 `false`、`allowed` 仍为 `null`
      ⇒ 它在**默认档**就零容忍（走 `allowed === null` 那条零容忍分支）。
      这样每一批只翻自己要翻的那条，先还完的不会被后还的拖着。同步改了三处随动文字：
      规则标题去掉「（--strict）」、`allowed === null` 分支的注释、结论区那句写死「这两条」的提示
      （原文在 0 待还时会误导，且 R2 毕业后已不可能出现在 `warnings` 里）。

      **定向破坏留痕**（护栏必须证明真会红）：摘掉 `settings/FieldPermissionPage.tsx` 的那一处
      `width={640}` ⇒ `pnpm ui:check` **退出码 1**，报「【R2 承载表单的 Modal 必须显式定宽】1 处 /
      `src/pages/settings/FieldPermissionPage.tsx` / 第 158 行」，且提示区**只剩 R3**。
      还原后 `md5` 与备份**逐字节相同**（`2c48173b3662e65b6fdc9361bdb93a46`）、
      `git diff --numstat` 回到 `1 insertions / 1 deletions`、门禁复绿退出码 0。

      **门禁**：`typecheck` / `lint` / `i18n:check` / `menu:check` / `perms:check` / `ui:check`
      （白名单仍冻结 **53** 处，未新增违规）全过；
      `test:coverage` 退出码 0、**Test Files 83 passed (83) / Tests 386 passed (386)**
      ——与改动前**同一个数**（本项只加 `width` 属性，不加不减用例）。
      两个档的收尾读数（本项提交前复跑）：默认档 `✓ … 既存债 53 处`、**exit 0**，
      提示区只剩 R3、**R2 一行都不出现**；`--strict` 档 `✗ … 90 处问题`、**exit 1**，
      且 90 处**全部**归 R3（`grep '【R'` 只有 R3 一行）——即 R2 在两个档里都不再产生任何条目。

      ⚠️ **上段的 `90` 与上一段的探针读数 `R3 90/92` 是「本项提交那一刻」的快照，不是今天的读数。**
      同日晚些时候发现的 **T046**（`readSource` 未剥注释）把 R3 订正为 **89 处 / 89 候选点**：
      那多出来的 1 处不是真实违规，而是 `customers/CustomerListPage.tsx` 里 P2 作者**解释 R3 修法的注释**
      （注释正文里写着 `<Col span={12}>`，被扫描器当成了标签）。
      提交那一刻的读数**原样保留**（它是 T040 证据链的一部分），只是不当今天的口径用。
      其余六个规则（R1 32/32 · R4 5/148 · R5 6/149 · R6 10/10 · R7 0/21 · R2 0/55）**逐数未变**——
      即本项的证据**没有一条**被这次修复动过。

> ✅ **2026-09-15 用户裁决：改序——先做 3.3，再做 3.2。**
> 依据是上面重数出来的那张表：R3 的 **88/90** 落在横向表单内，3.2 的地盘上只有 **2** 处。
> 先做 3.3 能让 **T045（R3 翻 error）第一次变得够得着**（90 → 2），代价是 3.2 的密度收益延后。
> 因此**下一批是 T042（3.3）而不是 T041**；T041 的原文与订正**一律保留**，只是排期后移。
> （本行是排期裁决，不改动 T041/T042 的任何既有文字。）
>
> 📌 **裁决原文里的 88/90 与 90 → 2 一律保留**（它们是被裁决时的那张表的读数）。
> 同日的 **T046**（`readSource` 剥注释缺陷）把那张表订正为 **87/89**、余额 **89 → 2**——
> 两个数都变了 1，**裁决依据与结论一个字都不受影响**（横向表单仍是 87/89 处、3.2 的地盘仍是 2 处）。

- [x] **T041** ✅ **已完成**（2026-09-15）3.2 其余 `layout="vertical"` 的表单（原文记 **43 个**）—— **密度收益最大的一批**
      （单列堆叠 → 两列栅格，表单高度直接减半）。
      ⚠️ 注意不要把"43 个纵向**表单**"与"58 个承载表单的**弹窗**"混为一谈（§2.2 与 §2.4 是两个分母）。
      ⚠️ **开工前必须重数本项的分母，不得沿用 43**（T040 执行时实测）：产品代码里
      `layout="vertical"` 声明为 **50 处 / 43 文件**（含测试则 51——多出的 1 处是 2026-09-15
      随 Enter 特性加进 `FormModal.test.tsx:165` 的），与 43 差 7，两个数口径不同（「表单」vs「声明」）。
      （旁证：本文件 T0xx 的决策核对表里早就记过「全库仍有 **50** 处 `layout="vertical"`」，
      即 43 与 50 的差**在写下 43 的时候就已经存在**，只是当时没被对上。）
      其中 **20 处就在 T040 刚碰过的 22 个弹窗里**（另 2 个 R2 弹窗的表单不是 vertical：
      `EmailCampaignPage.tsx:237`、`VisitListPage.tsx:268`）——与 T040 **大面积重叠但非完全重合**。
      重叠部分**不是重复劳动**：T040 改的是 `<Modal>` 上的宽度，本项改的是表单**内部**的栅格。
      但同一文件会被碰第二次，故**建议按文件聚合执行**（一个文件一次改完两件事），
      而不是先扫全库、再回头补第二轮。

      ✅ **分母已重数**（2026-09-15，T040 提交后、本项开工前；只读探针 + 门禁自己的扫描器各跑一遍，
      两个独立实现互相对账）：

      | 口径 | 读数 | 说明 |
      |---|---|---|
      | `layout="vertical"` **声明**（产品代码） | **50 处 / 43 文件** | 含测试 51/44 |
      | `<Form>` **区域**（门禁 `tagRegions`） | **44 处** | 区域法比声明法少 6——有 6 处 `<Form` 在**同一文件里配不出**配对的 `</Form>`（嵌套/自闭合），故两个口径**都不该被当成唯一真值** |
      | 其中**平铺单列**（表单区内无 `<Row>`） | **49 / 50** | 唯一已有分栏的是 `calls/CallRecordPage.tsx:215`（6 Item、1 行 2 列） |

      ⇒ **原文的「43」是"文件数"被当成了"表单数"**——含 `layout="vertical"` 的**文件**恰好也是 **43** 个。
      这解释了它为何与 50、44 两个数都对不上、又都差得不多。**本项以「50 处 / 43 文件（49 处平铺单列）」为准。**

      🛑 **本项的出口判据不成立，已就地订正**（这是开工前重数的最大收获）。
      `plan.md` 的 P3 行写「每批把自己那条规则从 warn 翻成 error」。R2 那条已按 T040 的订正执行掉
      （R2 无白名单，逐条毕业）。但 **3.2 翻不动 R3**——用**门禁自己的扫描器**把 R3 的 89 处逐条归类，实测：

      | R3 命中所在 | 处数 |
      |---|---|
      | 落在 `layout="vertical"` 表单内（= **本项**的地盘） | **2** |
      | 落在其余表单内 | **87** |
      | 其中在 `<Modal>` 内 | **88** |
      | 涉及文件 | **18** |

      （本表与 T045 的分布表同为 **T046 修复后**的读数；修复前是 90 / 2 / 88 / 88 / 19，
      差的那 1 处是 `CustomerListPage.tsx` 里解释 R3 修法的**注释**。）

      那 2 处还都在 `calls/CallRecordPage.tsx:223,228`——正是本项 50 处里**唯一已经分栏过**的那个文件，
      严格说不在「平铺单列 → 两列」的工作面里。
      ⇒ **本项做完，R3 顶多 89 → 87，翻不了 error。**「每批翻自己那条规则」这个模型**对 R3 不成立**，
      R3 只能在最后一批（T045）翻——这与 T045 已有的记录一致，但模型本身须记为订正。

      反过来看更能说明问题：R3 的 **87/89 落在横向表单内**，而 **3.3 的分母实测正是 22 个横向表单**
      （门禁区域法：`layout="horizontal"` **22 处**，与原文的「22 个」**逐数吻合**）。
      ⇒ **真正能把 R3 还到接近归零的是 3.3，不是 3.2**，尽管 3.2 的**密度收益**确实最大（两者不是一回事）。

      ⚠️ **于是本项需要一个新出口判据**（原「翻 error」够不着，两条路择一，属需用户裁决的排期/口径问题）：
      ① **保住排期、改判据**：本项出口 = 49 处平铺单列 → 两列栅格，且**新增的 `<Col>` 一律带断点**
         （`xs={24} md={12}`），使 R3 计数**不增**（顺带修掉 `CallRecordPage` 那 2 处则 89 → 87）；
         验据是「SC-005 视觉验收 + 计数不增」，**不是**「翻 error」。
      ② **改序**：先做 3.3（22 个横向表单，一处还掉 R3 的 87/89，离翻 R3 最近），再做本项。
        代价是密度收益延后，且 3.3 同样要过 SC-005。

      📌 **重数过程中探针自身踩的两个坑**（记下来，因为门禁里有一模一样的守卫，属同一类教训）：
      ① 裸 `lastIndexOf('<Form')` 会命中泛型实参 `<FormValues>` 里那一段（它自己就以 `<Form` 开头），
         于是「标签」取成 `<FormValues>`、整条表单被漏掉——**必须要求 `<Form` 后是非标识符字符**，
         这也正是 `scanTagEvents` 里那条「标签名必须整体匹配」守卫的由来。
      ② 标签闭合的 `>` 扫描必须跳过**泛型实参里的 `>`**（`<Form<LoginValues> …>`），
         否则标签原文被截在 `<Form<T>`，`layout="vertical"` 看不到。
      两轮下来探针读数 45 → 49 → **50**，直到与独立口径（`grep`）**逐文件对平**才敢采信。

      ✅ **开工前记录（2026-09-15，用户两项裁决 + 工作面重数）**

      用户裁决二（承接上面那个「本项需要新出口判据」的问题——他在裁决里选了「改序」，
      故此处按 `plan.md:111` 已记的结论取「**计数不增**」）：

      ① **纵向下限 = 200**（不用 `FormGrid` 默认的 256）。理由是一个**可推导的窗口**：
         竖向表单的标签在**上方**、不占横向空间，而默认下限 `labelWidth(96) + 160 = 256`
         是按**横标签**算的。要在默认档 `md`=640（可用宽 592）**恰好排两列**，下限须落在
         `3F + 2·16 > 592 ≥ 2F + 16` ⇒ `F ∈ (187, 288]`；取窗口内最贴近控件下限 160 的值 = **200**。
         窄视口下仍安全：320px 视口把弹窗夹到 288px，`⌊(288+16)/216⌋ = 1` ⇒ **自动退化成单列**。
      ② **工作面不含那 12 处「页面级」纵向表单**（`LoginPage:211`、`ChangePasswordPage:53`、
         `DataRetentionPolicyCreatePage:55`、`DataRetentionPolicyEditPage:86`、
         `ScheduledExportCreatePage:85`、`ComplianceExportPage:80`、`CustomerPortalPage:156`、
         `QuotaCreatePage:60`、`SlaCalendarPage:78`、`LandingPageView:79`、`PublicFormPage:72`、
         `PersonalCenterPage:155`）——它们是 **T043（3.4「13 个页面级表单」）**的地盘；
         且页面级容器是内容区（约 1000px 起），auto-fit 会排出 4–5 列，**列数上限**是
         `FormGrid` 今天不支持的另一件事（要动 P1 原语与它的测试），不在本批。

         ⚠️ **订正（2026-09-15，T043 完工时加。**原判词保留在上、不删**）**：
         ① 名单本身**准确**（12 个名字逐名对得上 T043 的重数），但「13 个页面级表单」这个**数字无支撑** ——
         重数为 **12 处 / 12 文件**，见 T043 记录；
         ② 「列数上限是 `FormGrid` 不支持的另一件事」**已不成立**：T043 给原语加了 `maxCols`
         （`formGridCap`），并按用户裁决给 6 处宽容器传 `maxCols={3}`；
         ③ 本段「工作面不含这 12 处」的判断**仍然成立**——T043 的施工面只有其中 6 处，
         另 6 处各缺「宽容器」或「≥2 相邻成对字段」之一，逐条理由见 T043 记录。

      工作面重数（探针 v2：与门禁扫描器同源，另自带「跳过泛型实参」的标签扫描；读数与上面的 **50/43 对平**）：

      | 口径 | 处数 |
      |---|---|
      | `layout="vertical"` 的 `<Form>` 声明 | **50 处 / 43 文件** |
      | 其中在弹窗内 / 页面级 | **38 / 12** |
      | 平铺单列（表单区内无 `<Row>`） | **49**（唯一已分栏的是 `CallRecordPage.tsx:215`） |
      | 平铺单列且 `<Form.Item>` ≤ 1（分列无意义） | **11**（弹窗内 8 + 页面级 3） |
      | ★ **本批工作面 = 弹窗内 · 平铺单列 · `Item ≥ 2`** | **29 处** |
      | 另加 `CallRecordPage.tsx:215`（已分栏，改造成 `FormGrid` 以还掉 R3 余下 2 处） | **1 处** |

      | 下限 | 工作面上真的会分列 | 列数分布 |
      |---|---|---|
      | 256（`FormGrid` 默认） | 11 / 38 | 1 列×18、2 列×11 |
      | **200（裁决采用）** | **28 / 38** | **2 列×26**、3 列×2（`width=680/720` 两个弹窗） |
      | 160（= `MIN_FIELD_WIDTH`） | 29 / 38 | 2 列×16、**3 列×13** |

      ⚠️ **本批不包「一列的栅格是空动作」的形态**：那 11 处 `Item ≤ 1` 的纵向表单**不包 `FormGrid`**
      （沿用 P2 `research.md` §12.2 对 `TagListPage` 的同一条判断）。但 **`CurrencyRatePage.tsx:144` 包**
      ——它 4 个字段、弹窗 420 宽，`floor 200` 下算出来就是 1 列（空动作），仍包的理由是
      **机制的价值正在于自己跟着容器变**：将来那个弹窗加宽到 480，它自动变两列；不包则永远单列。
      判据因此写作「**`Item ≥ 2` 的弹窗内纵向表单一律上栅格**」，**不是**「算下来会分列的才包」。

      🛑 **判据订正（2026-09-15，逐子节点分类之后；上面那句**不删**，按纪律留痕）**：
      上面那句是按**数量**写的，实际逐节点看过**控件类型**之后，真正的判据是
      「**≥2 个相邻成对字段**」——`Item ≥ 2` 只是它的必要条件。差别在三处：

      | 位置 | 顶层原子 | 为什么不上栅格 |
      |---|---|---|
      | `ApprovalCenterPage.tsx:218` | `{actionType === 'transfer' ? <Select> : <TextArea>}` | 三元的两个分支**互斥**，任一时刻只有 1 个字段（且一边是整行 TextArea） |
      | `AtRiskCustomersPage.tsx:136` | `Select` + `TextArea` | 成对字段只有 1 个（TextArea 是整行项） |
      | `VisitListPage.tsx:307` | `Input` + `TextArea` | 同上 |

      于是工作面 **30 → 27**（这三处**保持原样**，与「一列栅格是空动作」同一条理由）。
      反向的一例是 `SlaPolicyListPage.tsx:197`：`respondHours`/`resolveHours` 原在
      `display:flex` 的 `<div>` 里、`priority`/`enabled` 是它的兄弟——**拆包后**四者构成
      相邻成对字段串，故它**留在**工作面上（拆手搓容器正是本批判据要治的那种"写死两列"）。

      🛑 **出口判据订正**（原文的「翻 error」够不着，上文已述）：

      ① 29 处工作面 + `CallRecordPage` 全部改为容器驱动栅格；下限以**单一来源**落在
         `useFormMetrics.ts`（`VERTICAL_MIN_ITEM_WIDTH = 200`），**调用点不写裸数**；
         全宽项（`Input.TextArea`、`<Divider>`、内联表格…）按使用纪律第 1 条留在栅格**之外**；
      ② **R3 待还 2 → 0**（候选点 3 → 1，剩下的 1 是 `TagListPage` 改用 `flex` 的色板容器）；
         新增 `<Col>` = **0**（实际净减 2）⇒ **计数不增**成立；
      ③ 六道门禁 + `test:coverage` 全过、用例数不减；
      ④ SC-005 视觉验收——本批**必然产生可见变化**，按纪律仍由用户终判。
      ⇒ 副产品：**R3 归零后，T045 第一次真正够得着「翻 error」**。

      📌 **门禁扫描器的一处已知盲区**（本批探针栽在上面：v1 读数 45、真实 50）：
      `scanTagEvents` 在第一个 `>` 处截断标签原文，故 `<Form<LoginValues> … layout="vertical">`
      的属性根本读不到——与上文坑②同一成因，只是这次踩的是**门禁自己**。
      影响面已核：**今天没有任何规则读 `<Form>` 的属性**（R2 读 `Modal.width`、R3 读 `Col.span`），
      故不影响任何判据；但**下一个想按 `layout` 给表单分类的人必须先补这个跳过逻辑**。

      ✅ **完工记录（2026-09-15）**——**27 个表单 / 24 个文件 / 29 个 `<FormGrid>`**：

      | 判据 | 目标 | 实测 |
      |---|---|---|
      | ① 工作面改容器驱动栅格 | 30 处 | **27 处**（3 处按上面的判据订正排除） |
      | ② R3 待还 | 2 → **0** | **0 处待还（1 个候选点）**，`--strict` 档亦绿（**= exit 0**，见下） |
      | ③ 用例数不减 | ≥ **386** | **390**（83 文件；`useFormMetrics.test.tsx` 8 → **12**，新增 4 条钉**下限推导**） |
      | ④ SC-005 视觉验收 | 用户终判 | 未做（本批**必然可见变化**，见下） |

      下限走**单一来源**：27 个调用点全部写 `minItemWidth={VERTICAL_MIN_ITEM_WIDTH}`，
      **无一处裸数**（可复算：全库 `minItemWidth=` 的读数 = 29，与栅格数逐数吻合）。

      **列数分布（按各弹窗实测宽度算，不是估）**：**2 列×24、3 列×2、1 列×1**——
      3 列的是 `ContractTemplateListPage`（680）与 `KnowledgeArticleListPage`（720）；
      1 列的是 `CurrencyRatePage`（420），即上文那处"机制接好、今天算下来仍是空动作"的样板。

      **拆掉的手搓布局（这批真正的"删代码"）**：`CallRecordPage` 的 `<Row>` + 2 个 `<Col span={12}>`、
      `CustomerDetailPage` 两个 `display:flex` 的 `<div>`、`MailSyncPage` 两个（`flex:2` / `flex:1`）、
      `SlaPolicyListPage` / `StageActionTemplatePage` / `CustomFieldListPage` 各一个 `alignItems:'flex-end'` 的、
      `AnnouncementPage` 的 `<Space size={32}>`；连同它们身上的 `style={{ flex }}` / `marginBottom: 0`
      ——那是**为补偿容器而写死的死样式**，一并删除。**可见变化**：`MailSyncPage` 的 host:port
      由 **2:1 变等宽**，其余是"成对字段改排两列"。

      **两处定向破坏**（各自单独做、做完**逐字节还原**，`sha256sum -c` 校验回原样）：

      ① 下限 `200 → 300`（踢出上面那个窗口）：`useFormMetrics.test.tsx` **3 条变红**，红的正是
         「放得下两列」`616 > 592`、「比横向下限低」`300 > 256`、「320 视口退化成单列」三条；还原后 12 条转绿。
      ② 在 `CallRecordPage` 把 `direction`/`result` 包回 `<Row><Col span={12}>`：
         `ui:check` **0 → 1 处待还（候选点 1 → 2）**，`--strict` **exit 1** 且报的就是 R3；还原后转绿。
         ⇒ 这条同时证明「R3 = 0」是**活读数**而非空过，也是 T045「翻 error」够得着的前提。

      **`--strict` 两份读数的时点**（本批完工后现量，2026-09-15）：

      | 时点 | 默认档 | `--strict` |
      |---|---|---|
      | T042 完工（R3 = 2 处待还） | exit **0** | exit **1**，提示区打印 `R3（…--strict）：2 处待还（3 个候选点）` |
      | 本批完工（R3 = 0 处待还） | exit **0** | exit **0**，**提示区整块消失**、只剩结论行 |

      ⇒ R3 归零后 `--strict` **首次全绿**，这正是 T045「翻 error」够得着的前提（原文那句
      「计数归零那天把门禁整档换成 `--strict`」的**触发条件今天才真正满足**）。另附带一个副作用：
      本批之后**默认档与 `--strict` 都返回 0**，故定向破坏的可观测差异只能落在
      **`--strict` 的退出码**与 R3 计数上——这也是下面破坏②的取证口径。

      ⚠️ **一次全量复跑的假红留痕**（不静默、不改用例）：第一次 `test:coverage` 有 1 条**非本批文件**的
      用例失败——`ChangePasswordPage.test.tsx > 两次新密码不一致时提示错误且不提交（FR-006）`，
      `Unable to find role="alert"`，该文件整档耗时 **4204ms**；本批 27 个表单没碰过它，
      单独跑该文件 **674ms 通过**。**原地复跑同一条命令**：83 文件 / **390 用例全绿**。
      按本仓纪律记为**负载下的一次性假红**。
- [x] **T042** ✅ **已完成**（2026-09-15）3.3 其余横向表单（**22 个**，全部已设 `labelCol`；把 `labelCol` 定宽改用 `useFormMetrics`）
      —— **本项是用户裁决后的下一批**，也是 **R3 的主战场**（89 处里 87 处在此）。
      ✅ **分母已重数**（2026-09-15，T046 修完 `readSource` 之后；用**门禁自己的扫描器**逐表单实测，
      22 个横向表单逐条有据，可复跑复算——探针是 `scripts/` 下的 dot 前缀临时副本，跑完即删）：

      | 口径 | 读数 |
      |---|---|
      | `layout="horizontal"` 的 `<Form>` 区域 | **22**（与原文的「22 个」**逐数吻合**） |
      | 其中在 `<Modal>` 内 | **18** |
      | `labelCol` 已走 `useFormMetrics`（P2 样板 4 页） | **4**：`InvoiceListPage:215`、`TagListPage:133`、`ProductListPage:253`、`CustomerListPage:406` |
      | `labelCol` 仍是写死 flex | **18** |
      | 写死值分布 | `100px` ×10、`90px` ×5、`70px` ×1、`80px` ×1、`110px` ×1（= 18） |
      | 这 22 个表单内的 R3 命中 | **87**（候选点**也是 87**——每个候选都是违规） |

      ⇒ **R3 的 87 处（= 89 − 2 处纵向）一处不漏地全在这 22 个横向表单里**，
      与 T045 那张 18 文件分布表**逐数对得上**。
      即**用户裁决书里「3.3 一处还掉 R3 的 87/89」这句，经门禁自己的扫描器复核成立**。

      🛑 **原文没有、开工前必须先分清的三个例外**（否则「22 个」会被当成 22 次同一个机械变换）：

      1. **1 处在已迁过的页上**：87 处里 **86 处**在 18 个写死的表单里，剩下 **1** 处是
         `tags/TagListPage.tsx:140`——它的 `labelCol` 早已走 metrics。那是个 `span={2}` 的**色板**：
         `COLOR_OPTIONS.map` 出一排 24px 色块，`<Row gutter={[4,4]}>` 按 2/24 排。
         **这不是 R3 本意要治的「写死两列、无断点」字段布局**，而且 **`FormGrid` 在这里是错的工具**
         （它的轨道下限是 `labelWidth + 160 = 256px`，拿来排 24px 色块会一列一个色块）。
         正解是**声明意图**：加 `flex="0 0 auto"`（R3 的豁免属性之一）或改成 flex 容器，**不换 `FormGrid`**。
      2. **`span` 不是只有 12**：87 处按 `span` 分成 **61 处 `span={12}`（成对字段）+ 25 处 `span={24}`（整行独占）**
         （+ 上面那 1 处 `span={2}`）。逐表单：`WorkflowRuleListPage` 13 = 12×2 + 24×11、
         `ContractListPage` 9 = 12×7 + 24×2、`OpportunityListPage` 6 = 12×2 + 24×4、
         `OrderListPage` 6 = 12×4 + 24×2、`TicketListPage` 6 = 12×3 + 24×3、
         `TaskListPage` 6 = 12×5 + 24×1、`QuoteListPage` 3 = 12×2 + 24×1、
         `RoleListPage` 5 = 12×4 + 24×1，其余 10 个表单全是 12。
         ⇒ 每个表单都要**先分成两组**：`span={12}` 的进 `FormGrid`，`span={24}` 的**搬到 grid 之后做兄弟节点**。
         这不是美化——`FormGrid` 使用纪律第 1 条写明「全宽项放进 grid 会悄悄退化成 N 列里的一列」，
         写错了就是**看得见的版式错误**（备注/明细/长文本被压成一列）。
      3. **`<Row>` 一律要拆掉**，不能只改 `Col`：纪律第 2 条「同一表单内不得混用 `FormGrid` 与 `<Col>`」。
         22 个表单**都有 `<Row>`**（逐表单实测 `区内<Row>=Y`），故每一处都是
         「拆 `Row`/`Col` + 分两组 + 换 `labelCol` 取数」三步，不是「给 `Col` 加断点」一步。

      **出口判据（本项自己的，替代原「翻 error」）**：
      ① 22 个表单的 `labelCol` **全部**由 `useFormMetrics` 提供（写死 flex 归零）；
      ② 表单内 `<Col>` 归零 ⇒ **R3 由 89 降到 2**（剩下的 2 处是 `CallRecordPage` 纵向表单里
      刻意分栏的两列，不在本项工作面内）；③ 六道门禁 + `test:coverage` 全过、用例数不减；
      ④ 视觉验收走 SC-005（本项**必然产生可见变化**：弹窗里的表单由写死的两列变成容器驱动的 auto-fit，
      窄弹窗下会退成单列——**这正是要修的东西**，但按纪律仍须由用户终判）。

      ---
      ✅ **完工记录（2026-09-15）**——**19 个页面文件**，R3 由 **89 → 2**（判据②达成），
      写死 `labelCol` **归零**（判据①达成），六道门禁 + `test:coverage` 全过（判据③）。
      判据④（SC-005 视觉验收）**尚未执行**，见文末遗留。

      **逐文件（原 `labelCol` 取自 `git diff` 的删除行，不是回忆；R3 处数取自门禁扫描器）**：

      | 文件 | 原写死值 | 本项还掉 R3 | 分组情况 |
      |---|---|---|---|
      | `WorkflowRuleListPage` | **110px** | 13 | 12×2 + 24×11；成对的两项另嵌一个栅格（原本就在一条 label 下） |
      | `LeadListPage` | 100px | 9 | 12×9 |
      | `ContractListPage` | 100px | 9 | 12×7 + 24×2（`renewedFromId` / `remark` 留栅格外） |
      | `CampaignListPage` | 100px | 6 | 12×6 |
      | `OpportunityListPage` | 100px | 6 | 12×2 + 24×4（金额上下限 / 自定义字段 / 备注） |
      | `OrderListPage` | 100px | 6 | 12×4 + 24×2（描述 + 付款计划块：标题/明细表/合计） |
      | `TaskListPage` | 100px | 6 | 12×5 + 24×1 |
      | `TicketListPage` | 100px | 6 | 12×2 + 24 + 12 + 24 + 24 ⇒ **两个栅格**（12 被 24 隔断） |
      | `ContactListPage` | 100px | 5 | 12×5（客户 / 备注两头各一个整行项） |
      | `RoleListPage` | 90px | 5 | 12×4 + 24×1 |
      | `SalesOpportunityListPage` | 100px | 4 | 12×4 |
      | `QuoteListPage` | 100px | 3 | 12×2 + 24×1（原来是**两个** `<Row>`，各带一个 gutter） |
      | `ApprovalFlowPage` | 90px | 2 | 12×2 |
      | `EmailTemplatePage` | **70px** | 2 | 12×2（70px 是全库最窄的写死值） |
      | `OnlineFormPage` | 90px | 2 | 12×2 |
      | `SegmentListPage` | **80px** | 2 | 12×2 |
      | `TagListPage`（**色板，例外①**） | 已走 metrics | 1 | `span={2}` → `flex="0 0 auto"`，**未换 `FormGrid`** |
      | `EmailCampaignPage` | 90px | 0 | 表单内**没有** `<Col>`，只换 `labelCol` 取数 |
      | `VisitListPage` | 90px | 0 | 同上 |
      | 合计 | 18 个写死（100×10 / 90×5 / 70 / 80 / 110）+ 1 个已 metrics | **87** | 与开工前重数的分布**逐数吻合** |

      **`TicketListPage` 的「两个栅格」是本次唯一的结构判断**，也是三个例外里②（`span` 不是只有 12）
      唯一真正咬到的表单：`12,12,24,12,24,24` 里那个 `description`（`TextArea`）夹在两段成对字段中间，
      而栅格**不能跨越全宽项**（纪律第 1 条），故拆成前后两个栅格。
      顺带记下一处**有意的可见变化**：原先那个孤零零的 `span={12}`（`priority`）右半永远空着，
      改后按容器定宽 ⇒ **变成整行**。这一处已写进该文件的注释，留给 SC-005 一并看。

      **判据①的取证口径**（`写死 flex 归零` 这句话是怎么量的）：
      - `grep -rn "labelCol={{ flex: '" src/` ⇒ **0 处**（本仓没有别的口径能"漏掉"一个表单，因为模式是字面量）；
      - `measure-ui-baseline.mjs` 的「labelCol flex 取值分布」一节 ⇒ **空**（该脚本原本会列出 `100px × N` 这种行）；
      - 临时探针（门禁同一套正则）⇒ `layout="horizontal"` 的 `<Form>` **= 22**、其中 `labelCol` 取自
        `useFormMetrics` **= 22**、不是的 **= 0**。三个读数互相独立、结论一致。
      - ⚠️ **这一条今天没有门禁守着**：`measure-ui-baseline.mjs` 只是测量脚本，`check-ui.mjs` 里没有
        管 `labelCol` 的规则。故"归零"是**本次实测**，不是**每次都会红**的护栏——见文末遗留。

      **判据②的读数**：门禁默认档 `R3（…--strict）：2 处待还（3 个候选点）`，逐文件明细只剩
      `src/pages/calls/CallRecordPage.tsx` 的 2 处（`第 223 行：<Col span={12}>` 那对，纵向表单，本项工作面之外）。
      候选点 3 = 那 2 处 + `TagListPage` 那 1 处（有 `flex` 无 `span` ⇒ 只算候选不算违规），**逐数对得上**。

      **判据③的读数**：`typecheck` / `lint` / `i18n:check` / `menu:check` / `perms:check` / `ui:check` 六道全过
      （`ui:check` 收尾「白名单内冻结的既存债 **53** 处，未新增违规」）；`pnpm run test:coverage`
      ⇒ **83 文件 / 386 用例全通过、退出码 0**，与基线**逐数相同**（用例数不减）；
      statements 68.55 → **69.09**（代码变少，分母效应，非新增覆盖）。

      **定向破坏留痕（两次，各自逐字节还原）**：破坏点在 `OrderListPage.tsx`，改前/还原后
      `sha256[:16]` 两次都是 **`c9121c905cf533da`**。
      1. **栅格里又写死一列**：把 `title` 那项用 `<Col span={12}>` 包回去 ⇒
         `--strict` 由「2 处」变「**3 处**」，且新增的那条正是 `OrderListPage`（报行号与修法）；
         还原后回到「2 处」。
      2. **`labelCol` 写回定宽**：把 `labelCol` 改回 `'100px'` ⇒ `measure-ui-baseline.mjs` 的分布
         由**空**变成 **`100px × 1`**；还原后回到空。
      ⚠️ **退出码这一栏要看清**：`--strict` 在**基线**就是 `1`（因为 `CallRecordPage` 那 2 处在，
      这是判据②刻意留的），故破坏 1 的可观测差异是**计数 2 → 3**，**不是**「绿 → 红」。
      把"退出码没变"当成"护栏没反应"会得出相反结论。

      **两处口径提醒（都是本仓已记过的教训，这次又撞上）**：
      - **只采信剥注释后的读数**。本批给 19 个文件写了说明注释，注释里**引用了** `<Col span={12}>` 这类旧写法
        （例如 `TicketListPage` / `SegmentListPage` / `ApprovalFlowPage` 的注释）。
        拿原始 `grep` 数 `<Col` 会把**代码自己的说明书当成违规**——实测至少有 **9 处** `<Col` 落在注释行上。
        这正是 **T046** 修掉的同一个陷阱，故本记录里的 R3 一律引用**门禁**读数。
      - **`<Col` 页面级总数**在 `measure-ui-baseline.mjs` 口径下是 **86**（其中带断点 29、只写死 span 57）。
        这 57 **全部在表单之外**（表单内的已归零），是各页自绘统计块/看板/条件构造器的固定配比。
        它们**不在 R3 的辖区**（R3 只管表单内），故 R3 归零与这 57 并存**不矛盾**。

      **遗留（本项未做，供后续排期，不静默）**：
      1. **判据④ SC-005 视觉验收**：本项必然改变 18 个弹窗表单的观感（写死两列 → 容器驱动 auto-fit；
         窄弹窗退单列；`TaskListPage` 的 `priority` 由半宽变整行），**须由用户终判**。
         在验收通过前，P3 的后续批次（T043/T044）不应视为"已获视觉背书"。
      2. **这 18 个表单的版式没有用例守着**：P2 的 4 个样板页各有一个 `.form.test.tsx`
         （断言"成对字段落在栅格内"），本批 19 个页面**没有**对应用例——本项只靠 R3 与 SC-005。
         若要把它们也纳入用例守护，应另立任务（属"扩护栏"，不在本项"还债"的字面范围内）。
      3. **`labelCol` 没有门禁**（见判据①的取证口径）：今天的"归零"靠实测，明天有人写回 `'100px'`
         不会有任何门禁变红。若要常态化，应给 `check-ui.mjs` 加一条 R8（或让 `measure-ui-baseline.mjs`
         的该项进入 CI），**这属于新增护栏，须先立规格**。

- [x] **T043** ✅ **已完成**（2026-09-15）**3.4 页面级纵向表单：给 `FormGrid` 加 `maxCols`，宽容器限 3 列**。

      **判据订正：13 → 12，工作面 12 → 6**（本项标题原写「13 个页面级表单」，**无支撑**）。
      用**门禁同源**的标签扫描（搬运 `check-ui.mjs` 的 `scanTagEvents` / `tagRegions`，
      只用它的「区域」口径判是否落在弹窗内，不自造启发式）实测：
      `layout="vertical"` 且**页面级**的 `<Form>` = **12 处 / 12 文件**——
      逐名就是 T041 开工记录（`:538-542`）里列的那 12 个，「13」找不到第 13 个。

      ⚠️ **这次重数顺带抓到我自己探针的两个 bug，都记下来**（口径错会自证不了，本仓有教训）：
      1. **`scanTagEvents` 在泛型实参处截断标签文本**：`<Form<DataRetentionPolicyRequest> … layout="vertical">`
        的 `e.text` 只到 `"<Form<DataRetentionPolicyRequest>"`，于是「在标签文本里找 `layout="vertical"`」
        会**静默漏掉 5 个文件**（读数 9 vs 真实 12）。改用**区域切片**（`code.slice(r.start, r.start+600)`）后对平。
      2. **「弹窗内」不能只认 `<Modal>`**：`CustomerListPage.tsx:460` 的 `transferForm` 在 **`<FormModal>`** 里
         （封装组件，`tagRegions(code,'Modal')` 看不见它），被误判成页面级 ⇒ 读数虚增到 13。
        把 `FormModal` 一并算作弹窗区域后回到 **12**。
      ⇒ 两次误读合起来正好演示了「口径的边界要自证」：**读数对不上历史记录时，先怀疑探针**。

      12 处的容器宽**异质**（312 / 360 / 404 / 432 / 520 / 560 / 820 / ≈984–1000px）。
      满足本批口径「**宽容器 + ≥2 个相邻成对字段**」的只有 **6 处**：

      | 页面 | 路由 | 容器 | 栅格子项 | 相邻成对字段 |
      |---|---|---|---|---|
      | `QuotaCreatePage` | `/quotas/create` | 1000 | 6 | year / quarter / teamId / userId / amount / periodRange |
      | `ComplianceExportPage` | `/data-retention/compliance-export` | 1000 | 3 | entityType / userId / exportFormat |
      | `DataRetentionPolicyCreatePage` | `/data-retention/create` | 1000 | 3 | entityType / retentionDays / actionType |
      | `DataRetentionPolicyEditPage` | `/data-retention/:id/edit` | 1000 | 3 | 同上 |
      | `ScheduledExportCreatePage` | `/exports/scheduled/create` | 1000 | 2 | exportFormat / periodType |
      | `CustomerPortalPage:156` | `/portal` | 520 | 3 | phone / email / title |

      **未纳入的 6 处与理由（逐条实测/源码可查，不静默）**——判据是「宽容器 **且** ≥2 相邻成对字段」，
      这 6 处各缺其一：

      | 页面 | 缺哪半 | 证据 |
      |---|---|---|
      | `LoginPage:211` | 宽容器 | 浏览器实测 `<form>` 宽 **360px** ⇒ ⌊(360+16)/216⌋=**1 列**，上限 3 永不生效 |
      | `ChangePasswordPage:53` | 宽容器 | 实测 **404px** ⇒ 同样 **1 列** |
      | `SlaCalendarPage:78` | 宽容器 | 源码 `<Form style={{ maxWidth: 560 }}>` 自限 ⇒ ⌊(560+16)/216⌋=**2 列**，上限 3 永不生效 |
      | `PersonalCenterPage:156` | 成对字段 | 表单只在编辑态存在，且**只有 1 个字段**（`displayName`） |
      | `LandingPageView:79` | 成对字段 | 字段由 `lp.form.fields.map()` **后端配置驱动**，数量编译期不定 |
      | `PublicFormPage:72` | 成对字段 | 同上（`meta.fields.map()`） |

      **原语：`maxCols` 是「抬高轨道下限」，不是写死列数**（这是本批唯一的新能力，写在
      `formGridStyle.ts` 的 `formGridCap` 里）。auto-fit 的轨道数是 `k = ⌊(W+g)/(T+g)⌋`；
      要保证 `k ≤ C` 须取 `T > (W+g)/(C+1) − g`，于是

      ```
      T_cap = calc((100% + <gutter>px) / (maxCols + 1) - <gutter>px + 1px)
      T     = min(100%, max(<minItemWidth>px, T_cap))
      ```

      **`+1px` 这个 epsilon 是承重的**：`T` 恰等于右端时 `k` 正好落在 `C+1`，而 `W` 是百分比、
      由浮点布局解析 ⇒ 会在 `C` 与 `C+1` 之间**来回抖**。`max(minItemWidth, …)` 则让切换点**连续**
      （两者在 `W = minItemWidth·(C+1) + C·g − 1` 处相等；200/3/16 代入 = **844px**）。
      `cols={N}` 与 `maxCols=N` 是两件事：前者 `repeat(N, minmax(0,1fr))` 硬列（320px 也挤成 N 列），
      后者只封顶；两者同传时 `cols` 赢；`maxCols` 为 0/负数/非整数按未传处理；`maxCols=1` 合法。

      **判据①（宽页封顶）与判据②（窄容器不咬）——真实引擎实测**（`/quotas/create` 等 5 个宽页 + `/portal`）：

      | 时点 | 视口 | 容器 | 轨道 | 子项宽 |
      |---|---|---|---|---|
      | 修复前（下限用默认 256） | 1280 | 1000 | 3（靠 256 下限，**不是**靠上限） | 323 ×6 |
      | | 1920 | 1640 | **6** | ~260 |
      | 修复后（下限 200 + 上限 3） | 1280 | 1000 | **3**（靠上限） | 323 ×6 |
      | | 1920 | 1640 | **3** | **536 ×6** |
      | `/portal`（520，上限不该咬） | 1280 | 520 | 修复前 **1** → 修复后 **2** | 252 / 252 |
      | `/exports/scheduled/create`（2 子项落 3 轨） | 1280 | 1000 | 3（**空轨道塌掉**） | 492 / 492 |

      **⚠️ 本批内部的一次自我订正（订正不静默）**：第一稿 6 个调用点**只传了 `maxCols`、没传 `minItemWidth`**，
      于是它们静默用了 `FormGrid` 的**横向**默认下限 **256**（`labelWidth 96 + 160`），
      而不是 T041 定下的**纵向**下限 **200**（`VERTICAL_MIN_ITEM_WIDTH`）。
      暴露它的是一次**测量与模型矛盾**：探针在 `/portal` 量到 **1 轨**、而按 200 的模型应是 2 轨。
      我没有改模型去迁就读数，而是先做了受控引擎对拍证明模型无误，于是定位到调用点——
      这同时说明 1280 下看到的「3 列」当时**并非上限之功**（上限只在 1920 才显形：6 → 3）。
      修法是把 6 处补齐 `minItemWidth={VERTICAL_MIN_ITEM_WIDTH}`，**单一真源不破**（6 处引用常量，无裸数字）。

      **判据③的读数**：`typecheck` / `lint` / `i18n:check` / `menu:check` / `perms:check` / `ui:check`
      六道全过（`ui:check` 收尾「白名单内冻结的既存债 **53** 处，未新增违规」，
      `R3：0 处待还（1 个候选点）`——**与 T041 完工时逐数相同，本批未使 R3 回退**）；
      `pnpm run test:coverage` ⇒ **83 文件 / 398 用例全通过、退出码 0**（基线 390 ⇒ **+8**，用例数不减），
      statements 69.15 / branches 73.81 / functions 36.78 / lines 69.15（均高于 83 文件基线）。

      **定向破坏留痕（两次，各自逐字节还原；`sha256sum -c` 7/7 OK）**：

      1. **破坏原语**：把 `formGridCap` 的 `+ 1px` 改成 `+ 0px` ⇒ `FormGrid.test.tsx`
         **4 条转红、15 条仍绿**，且红的正是 epsilon 承重的那几条不变式——
         模板串逐字节比对、**848px 排出 4 列**（`expected 4 to be less than or equal to 3`）、
         边界 **844 由 3 变 4**、切换点连续性 2 vs 1。还原后 **19/19 全绿**。
         ⇒ 这条护栏**可证伪**，且它红在声称的那条不变式上。
      2. **破坏调用点**：摘掉 6 处的 `maxCols={3}`（只留纵向下限）⇒ 真实引擎读数
         宽页在 1280 由 **3 轨变 4 轨**（6 个子项被挤成 238px、排 4+2 **破行**）、
         在 1920 由 **3 轨变 7 轨**；而 `/portal` **纹丝不动**（520px 容器上限本就不咬）——
         正是「上限在承重、且只在宽容器上承重」的证据。还原后逐数回到破坏前。
         ⚠️ **这条不使任何已提交的用例变红**：T043 的 6 个调用点**没有**配套页面用例
         （见遗留 2，与 T041 同一缺口）。它的证据力来自**真实引擎读数**，不是来自护栏，
         此处如实标注、不含糊。

      **本批的可见变化**（留给 SC-005 一并看）：这 6 个表单此前是**平铺单列**，
      改后宽页 **3 列**（`QuotaCreatePage` 6 项排 3+3；三个 data-retention 页各 3 项排一行；
      `ScheduledExportCreatePage` 只有 `exportFormat`+`periodType` 成对，各占一半 ⇒ **2 列**，其余项整行）、
      `/portal` **2 列**（`phone`/`email` 并排、`title` 换行；`description` 是多行文本、`priority` 被它隔开，
      两者**刻意留在栅格外**整行）。

      **遗漏（本项未做，供后续排期，不静默）**：
      1. **判据④ SC-005 视觉验收**：本批必然改变这 6 个页面级表单的观感，**须由用户终判**；
         在验收通过前 T044 不应视为"已获视觉背书"（沿用 T041 的同一条口径）。
      2. **这 6 处没有用例守着**（同 T041 遗留 2）：`maxCols` 的原语有 19 条纯函数/算术用例，
         但「某个页面该排几列」没有 committed 用例——真实引擎读数只存在于本记录与提交信息里。
         若要常态化，应另立「几何 e2e 护栏」类任务（本仓 092 正在做同型的事）。
      3. **`maxCols` 的档位（3）是**页面级**的裁决，不是通用默认**：`FormGrid` **不设**默认上限
         （弹窗窄容器里默认封顶会误伤）。窄容器页面（`/login` 360、`/account/password` 404、
         `SlaCalendarPage` 560）今天靠"容器本来就不够宽"天然单/双列，**不是**靠上限——
         若日后这些页面的容器被放宽，须重新裁决是否补 `maxCols`。
- [x] **T044** ✅ **已完成**（2026-09-15）3.5 详情页（077 系 4 页 vs 传统系 `Descriptions` 11 个文件两套版式）。

      **本项被用户裁决缩到一件可实证的事**，原状与裁决过程如实记下：

      **开工前实测到的三项事实（推翻了本项原有的前提）**：
      1. **它没有 FR、没有 SC、也没有门禁规则**——`check-ui.mjs` 的七条规则没有一条管 `Descriptions` 的 `column`；
         `spec.md` 的 FR-001~FR-014 里也没有一条要求详情页列数按断点响应。
      2. **它的另一半是明确的非目标**：本项标题里的「077 系 4 页 vs 传统系」若要"统一"，就得改版式、
         并统一 `StatCard` / `StatusTag` / `AmountDisplay` 三个组件——而 `spec.md` 的**非目标**已把
         组件采用明文排除。⇒ 本项**不能**按标题字面执行。
      3. **两套版式的真实差异轴只有 `column` 一个**：`bordered size="small"` 两边都有
         （我一度以为差在边框，读完整开标签后**当场订正**过，属本仓"读数对不上就怀疑探针"的同型）。

      **用户裁决（2026-09-15）**：**「缩到 column 统一 + 回填需求」**——只做 4 处 `column` 统一，
      并向 `spec.md` **回填一条 FR**（新增 **FR-015**），让本项的判据从此有据可依，
      而不是继续挂在一条并不存在的判据上。**不碰** `StatCard` / `StatusTag` / `AmountDisplay`。

      **改动（4 文件 / 4 处 `column` + 4 处 `span`）**：

      | 文件 | `column={2}` → | 全宽项 `span={2}` → |
      |---|---|---|
      | `quotes/QuoteDetailPage.tsx`（171） | `{ xs: 1, sm: 2, md: 3 }` | `span={3}`×2（188 驳回原因、192 备注） |
      | `tickets/TicketDetailPage.tsx`（195） | 同上 | `span={3}`×1（230 描述） |
      | `data-retention/DataRetentionExecutionHistoryPage.tsx`（107） | 同上 | （无全宽项） |
      | `exports/ScheduledExportExecutionHistoryPage.tsx`（112） | 同上 | `span={3}`×1（121 Cron） |

      于是 Track B 与 Track A（`ContractDetailPage` / `CustomerDetailPage` / `LeadDetailPage` / `OrderDetailPage`）
      **逐字同形**：`column={{ xs: 1, sm: 2, md: 3 }}` + 全宽项 `span={3}`。

      **⚠️ 一处实测出来的副作用，如实记下、不藏（它**不**改变本次的结论，但必须让后人知道）**：
      `span={3}` 会触发 antd 的**开发期警告**
      `[antd: Descriptions] Sum of column span in a line not match column of Descriptions.`。
      **A/B 对拍（同文件、同用例、同计数方法）**：`column={2}` + `span={2}` ⇒ **0** 条；
      `column={{…}}` + `span={3}` ⇒ **8** 条。**再隔离**：只把 `span` 改 3、`column` 保持 `2` ⇒ 仍是 **8** 条
      ⇒ **成因是 `span={3}`，与响应式 `column` 无关**。
      机制（读 antd `descriptions/hooks/useRow.js` 的 `getCalcRows`）：行是按 `count += span` **贪心**装的，
      `count > mergedColumn` 即记 `exceed`；于是**只要全宽项前面的单格项数不是 `column` 的整数倍**，
      `span={3}` 就会溢出，antd 把它**截到"剩余列数"**（`span: restSpan`）并告警。也就是说
      `span={3}` 表达的是"**填满本行剩余**"而不是"独占整行"，**这两种写法在像素上完全一致**
      （antd 的 `span="filled"` 也是同一语义，区别只是它**不告警**）。
      **这不是本项引入的新形态**：实测 Track A 的两个页面用例今天就已经在报这个警告
      （`ContractDetailPage` + `OrderDetailPage` 合计 **16** 条，**在我改动之前**，committed 状态）。
      ⇒ 本项让 Track B 与 Track A 一致，**包括这一条副作用**；告警仅在 `NODE_ENV !== 'production'` 下出现，
      不影响产物、不影响任何用例（4 个受影响文件的用例 22/22 全绿）。
      **收口办法（覆盖两条 Track，不在本项范围）**：把全宽项由 `span={3}` 改为 `span="filled"` ——
      像素不变、告警消失、且语义正确（"填满本行"）。这属**判据/写法变更**，须单独一项做并重新验证。

      **判据④/SC-005 终判（欠账，如实记）**：本项**必然改变这 4 个页面在 md 档（≥768px）的观感**——
      列数由写死 2 变 3，且全宽项不再是"自己独占一整行"，而是**与上一格共享末行**（上表那 4 处）。
      须由用户在 **SC-005** 的 1920/1440/1024/768/375 × 中英文 里一并终判；
      **在验收通过前不得读成"已获视觉背书"**（沿用 T041/T042/T043 的同一条口径）。

      **判据③的读数**：`typecheck` / `lint` / `i18n:check` / `menu:check` / `perms:check` / `ui:check`
      六道全过（`ui:check` 收尾「白名单内冻结的既存债 **53** 处，未新增违规」——本项**不涉及**任何门禁规则）；
      受影响的 4 个文件先单跑 ⇒ **22/22 绿**；随后 `pnpm run test:coverage` ⇒
      **83 文件 / 398 用例全通过、退出码 0**（与 T045 完工时逐数相同，用例数与文件数均未减少）。

      **顺带订正（同一批，T045 的尾巴）**：`spec.md` 里两处因 T045 退役而失效的描述已就地加 ⚠️ 订正、
      **原文一律保留不删**：FR-009 的「六条规则 + 先宽后紧（`--strict`）」（今为**七条**、且该档已退役）；
      「关键实体」里 `package.json` 的 `ui:check` / **`ui:check:strict`**（后者已删除）。

      **遗漏（本项未做，供后续排期，不静默）**：
      1. **`span="filled"` 收口**（见上）：改两条 Track 的全宽项写法，消掉 `exceed` 告警。
      2. **Track B 之外的同类写死仍有 5 处，本项按裁决**未动**：`components/SignSection.tsx`（`column={2}`）、
         `components/SurveyBlock.tsx`（`column={2}`）、`Personal/PersonalCenterPage.tsx`（`{ xs: 1, sm: 2 }` ×2）、
         `portal/CustomerPortalPage.tsx`（`column={1}` 与 `column={3}`）。
         它们**不在** T044 标题的"详情页"范围内，逐处是否该统一**未裁决**——本项不替它们下结论。
      3. **`Descriptions` 的 `column` 今天没有任何门禁**：本项完全靠人工逐处核实 + SC-005 兜底。
         若要常态化，同 T043 遗留 2 的思路，应另立一条规则或并入几何护栏。

- [x] **T045** ✅ **已完成**（2026-09-15）**R3 从 `--strict` 翻成 error**（原文记 96 处，**当前实测 89 处 / 89 候选点**——以实测为准；
      原写「90 处 / 92 候选点」，那是 `readSource` 剥注释缺陷未修时的读数，订正见 **T046**），
      并删除 P2/P3 期间已还清的全部白名单条目。
      ⚠️ **本项的前一半已在 T040 内做完**：`check-ui.mjs` 原写「计数归零那天整档换成 `--strict`」，
      而整档切换会连带把 R3 也变成失败，故改为**逐条毕业**，R2 已按此法翻成默认档 error。
      本项开工时**只剩 R3 一条**，且**不要再动 R2**（它已零容忍，动它会把它退回 warn 档）。
      📌 **R3 的 89 处住在哪儿**（2026-09-15 用门禁自己的扫描器实测，供本项预估工作量）：
      落在 `layout="vertical"` 表单内仅 **2** 处，其余 **87** 处落在**横向**表单内、其中 88 处在 `<Modal>` 内
      （那 1 处不在弹窗内的同样在横向表单里），跨 **18** 个文件（按处数降序，合计 89，与门禁读数逐数吻合）：
      `WorkflowRuleListPage` 13、`ContractListPage` 9、`LeadListPage` 9、
      然后 `CampaignListPage` / `OpportunityListPage` / `OrderListPage` / `TaskListPage` / `TicketListPage` 各 6、
      `ContactListPage` / `RoleListPage` 各 5、`SalesOpportunityListPage` 4、
      `QuoteListPage` 3，然后 `ApprovalFlowPage` / `CallRecordPage` / `EmailTemplatePage` /
      `OnlineFormPage` / `SegmentListPage` 各 2、`TagListPage` 1
      （13+9+9 + 6×5 + 5×2 + 4 + 3 + 2×5 + 1 = **89**，18 个文件，可复算）。
      ⚠️ **本条原写「90 处 / 88 处 / 跨 19 个文件」**，是 `readSource` 剥注释缺陷（**T046**）未修时的读数：
      那多出来的 1 处是 `customers/CustomerListPage.tsx` 里 P2 作者**解释 R3 修法的那段注释**，
      不是真实违规。修缺陷后逐数变为 **89 / 87 / 18**。
      ⇒ **本项的绝大部分工作量在 3.3（22 个横向表单）而不是 3.2**，
      本项若要开工得早，只能靠 3.3 先落地。

      ---

      ### T045 完工记录（2026-09-15）

      **本项实际做的事比标题大**：标题只写了「R3 翻 error」，但 R3 是**最后一条** warn 档规则
      （R2 已于 **T040** 逐条毕业，见 `tasks.md` 的 T040 段与 `check-ui.mjs` 文件头）。
      R3 一翻，七条规则**全部**是默认档 ⇒ `--strict` 这个开关**不再改变任何行为**。
      于是本项同时**把「两档机制」整条退役**：`--strict` 连 `package.json` 的 `ui:check:strict`
      一起删除。**理由**：留着一个什么也不改变的开关，会让"它看起来更严"变成一个**假象**——
      下一个人会以为不加 `--strict` 就少了一层保护，而事实是两层已经并成一层。

      **改动面（`git diff --stat`：2 文件 / +33 −45）**，逐项：

      | 文件 | 删掉的东西 |
      |---|---|
      | `scripts/check-ui.mjs` | `const STRICT`；`const warnings`；`if (rule.strict && !STRICT) { warnings.push(…) ; continue }` 分支；末尾整个「提示（非失败）」打印块；7 个规则定义里的 `, strict: false`；文件头的「先宽后紧」一节（改写为**退役记录**）；`用法` 行里的 `--strict` |
      | `package.json` | `"ui:check:strict": "node scripts/check-ui.mjs --strict"` |

      R3 的规则定义变为与 R2 完全同形：`{ id: 'R3', …, allowed: null }`（**无 `strict` 字段**，
      因为不存在第二档了）。**R2 未动**（原文叮嘱过：动它会把它退回 warn 档）。

      **门禁读数（默认档，唯一的档）**：
      `✓ UI 规范校验通过（白名单内冻结的既存债 **53** 处，未新增违规）`、**exit 0**。
      被删掉的「提示（非失败）」区**此前在 R3 已归零时仍然逐次打印**
      `R3（…）：0 处待还（1 个候选点）…加 --strict 会把它当失败` ——即**债还完之后，那段提示只是在刷存在感**。
      收尾行也不再带 `；**--strict** 档` 后缀。

      **⚠️「删除已还清的白名单条目」这一句在 R3 上无事可做 —— 这是实测结论，不是被跳过的工作。**
      白名单的双向校验是**全局**的，对每个 `allowed != null` 的规则同时做四件事：
      命中文件不在白名单 → 红；白名单条目**一处也没命中** → 红；命中数 ≠ 登记数 → 红；
      `allowed === null` 的规则有命中 → 红。因此**一个全绿的门禁，本身就证明了没有任何条目陈旧**
      ——否则它已经是红的。而 R3（与 R2）的 `allowed` **本来就是 `null`**，即**它们从来没有过白名单条目**。
      ⇒ 白名单 53 处**全程未动、也不该动**；能"删除条目"的对象只可能是 R1/R4/R5/R6/R7 里某个文件的
      命中数下降，那属于各自的批次，不属于 T045。

      **定向破坏留痕（两次，各自逐字节还原）**：

      1. **破坏 R3 的判据落点**：`tags/TagListPage.tsx:145` 的 `<Col key={c} flex="0 0 auto">`
         改回 `<Col key={c} span={2}>`（正是本文件 §遗留第 7 条点名的那处色块，
         连它上方的注释都写着「原先写 `span={2}`」）⇒ 默认档 **exit 1**，报
         `【R3 表单内 `<Col>` 不得只写 `span`】src/pages/tags/TagListPage.tsx 第 145 行：<Col key={c} span={2}>`。
         还原后 `sha256sum -c` ⇒ **OK**。
         ⚠️ **如实标注**：这条破坏**不能**证明"此前会 exit 0"。那个断言来自**被删掉的代码路径**
         （`if (rule.strict && !STRICT) { warnings.push(…); continue }`），我没有为它再做一次两态对拍。
         它证明的是**默认档现在真的拦得住 R3**，这一点是本次实跑出来的。
      2. **破坏反假绿自检**：把 `MIN_CANDIDATES.R3` 由 `1` 改成 `99` ⇒ **exit 1**，报
         `【R3 自检失败】本规则只解析出 1 个候选点（预期 ≥ 99）`。既证明**自检是活的**，
         也顺带把候选点数量出来了。还原后 `sha256sum -c` ⇒ **OK**（本次破坏的落点就是本项要提交的文件本身）。

      **判据③的读数**：`typecheck` / `lint` / `i18n:check` / `menu:check` / `perms:check` / `ui:check`
      六道全过；`pnpm run test:coverage` ⇒ **83 文件 / 398 用例全通过、退出码 0**
      （与 T043 完工时**逐数相同**，用例数与文件数均未减少）。

      **本项不产生可见变化**（纯门禁改动）⇒ **不新增 SC-005 欠账**。

      **遗漏（本项未做，供后续排期，不静默）**：

      1. **`MIN_CANDIDATES.R3 = 1` 已是零余量——这是假红风险，不是假绿。**
         R3 的候选点由 **89 掉到 1**：T041/T042 是把 `Row`+`Col` **整组**换成 `FormGrid`，
         Col 本身被删掉（不是改写法），所以候选点跟着塌。今天那唯一的 1 个就是
         `TagListPage.tsx:145` 的色块行——它**刻意**用 `flex` 定宽而非 `FormGrid`（理由见 research.md §12.5）。
         **若日后这一行换成别的写法**（例如 `Flex` + `wrap`），候选点会掉到 **0**，
         门禁就会因为「R3 自检失败」**红在一个完全正当的重构上**。收口办法（均**不在**本项范围）：
         把 R3 的候选判据从「表单内的 `<Col>`」放宽为「表单内的**栅格容器**」（`Row` / `FormGrid`），
         或把 `MIN_CANDIDATES.R3` 降到 0 并写明为什么这一条不需要活性下限。
      2. **R3 的判据文本对"色块行"是错的**（**同一处**、老遗漏，仍在）：R3 的 `修复` 文案建议
         「换成 `FormGrid` 或至少补 `xs/sm/md/lg` 断点」，而 `TagListPage` 的色块行**两半都不适用**
         （全宽项本来就该在栅格之外，`FormGrid` 拿去排色块会一列一个）。今天它不出错只是因为那一行**没有** `span`
         ⇒ 不在命中集里。真要收口得让规则能区分"成对字段的 Col"与"按内容定宽的 Col"，
         属判据本身的变更，另立。
      3. **CI 首次强制 R3**：`.github/workflows/ci.yml` 的 `run: pnpm run ui:check`（`UI conventions (ui:check)` 步骤）
         跑的是**默认档**。所以 R3 此前的 89 处债、以及它翻档前后的全部行为差异，**从未进过 CI 的视野**；
         「R3 进 CI」这件事的第 1 天就是今天。这条不是欠账，是**本项真正的收益**，写在这里防止被误读成"只是翻了个开关"。
         **随本次一并订正的还有 CI 文件自身的注释**：它原写着「R3 仍躲在 `--strict` 后面，
         故**不能**在这里改成 `ui:check:strict`」——今天**两头都不成立**（脚本已无此选项；两档已并成一档）。
         按本仓「原文留痕」的规矩**原句保留在上、加 ⚠️ 作废说明**，不删。
         （引用一律用**锚字符串** `run: pnpm run ui:check`，不写行号——本次插入注释就会让行号漂移。）

- [x] **T046** ✅ **已完成**（2026-09-15）**门禁自身的缺陷：`readSource` 不剥注释，
      三条规则在拿注释里的散文当违规**。本项**不属于 3.x 的任何一批**——它是重数 T041 分母时顺带撞见的
      **真 bug**，而它直接决定 R3 的分母，故**先修再排期**。

      **缺陷**：`readSource(file)` 原返回 `{ code: raw, lines: stripComments(raw) }`——`lines` 是剥过注释的，
      `code` 是**原文**。而 R4/R5/R6/R7 用的是 `lines`，**R2 / R3 / `formItemTags()` 用的是 `code`**。
      两条轨并存，于是这三条规则会去管注释里的散文。

      **为什么这是「代码改不掉的红」**：`check-ui.mjs` 自己就是靠文档注释讲清每条规则的理由的。
      说明写得越清楚，越可能写出一个能让 R2/R3 命中的标签形状，而**唯一的"修复"办法是把说明删掉**。
      这不是假阳性噪声——它是**与文档为敌**的判据：想让人看懂规则，就得先让门禁变红。

      **实测到的具体案例（不是构造出来的）**：P2 迁移过的两页都在注释里写了旧写法，于是
      **它们已经还清的债被注释又记了回来**——
      `customers/CustomerListPage.tsx` 的注释正文含 `` `<Col span={12}>` ``（带 `span`、无断点 ⇒ **算一处命中**）
      与 `` `<Col>` ``（同一表单内不混用，⇒ 算一个候选点）；
      `products/ProductListPage.tsx` 的注释正文含 `` `<Col xs={24} sm={12}>` ``（**有断点 ⇒ 只进候选、不算命中**）。
      两页的**生产 JSX 里一处 `<Col>` 都不剩**（已全改 `FormGrid`）——即 R3 当时那 90 处里，
      有 1 处是**在指控一个已经修好的文件**，另外还虚增了 3 个候选点。

      **修法**：两条轨合并成一条，`readSource` 只返回剥过注释的版本——
      ```js
      function readSource(file) {
        const raw = readFileSync(file, 'utf8')
        const lines = stripComments(raw)
        return { code: lines.join('\n'), lines }
      }
      ```
      `stripComments` **逐行**输出（每个输入行对应一个数组项、行号不变），故 `lineOf` 与门禁报的行号仍然对得上；
      它**保护字符串字面量**（只剥注释），故 `attrOf` 从标签原文里取属性的逻辑不受影响。
      缺陷本身、证据与波及面都写进了该函数的文档注释，防止有人"顺手"把 `code: raw` 加回来。

      **波及面实测（改前 / 改后逐规则对账，同一份 `src`；把 `readSource` 切成两态各跑一遍）**：

      | 规则 | 修前 | 修后 |
      |---|---|---|
      | R1 | 32/32 | **32/32（未变）** |
      | R2 | 0/55 | **0/55（未变）** |
      | **R3** | **90/92** | **89/89** |
      | R4 | 5/148 | **5/148（未变）** |
      | R5 | 6/149 | **6/149（未变）** |
      | R6 | 10/10 | **10/10（未变）** |
      | R7 | 0/21 | **0/21（未变）** |
      ⇒ **只有 R3 动了**。逐文件对账（`PROBE-CAND <file> cand=… hits=…`，两态各跑一遍后 `diff`）显示差额**只有两行**：
      `CustomerListPage.tsx` `cand=2 hits=1` → **整行消失**（该文件已无任何表单内 `<Col>`）、
      `ProductListPage.tsx` `cand=1 hits=0` → `cand=0`。
      命中 90 → 89（−1）、候选点 92 → 89（−3）**逐数对得上**，没有第三处未经归因的变化。

      **两个档的复跑**：默认档 `✓ UI 规范校验通过（白名单内冻结的既存债 53 处，未新增违规）`、**exit 0**，
      提示区 R3 那一行改为 **89 处待还（89 个候选点）**；`--strict` 档 `✗ … 89 处问题`、**exit 1**，
      且 `grep CustomerListPage` = **0**（注释伪违规确已消失）。白名单仍 **53** 处未动。
      ⇒ **T040 的证据链没有被本项动过**：R2 的 `0/55`、白名单的 `53`、`test:coverage` 的 83/386 逐数不变，
      故 T040 那条「待还归零 _且_ 候选点不变」的出口判据仍然成立。

      **定向破坏留痕（本项是脚本缺陷，故破坏的是脚本而非生产代码）**：把 `readSource` 切回修前的两轨写法
      （`code: raw`）——**实测 R3 立刻回到 90 处 / 92 候选点**，`--strict` 的 89 处问题变回 90 处，
      且 `grep CustomerListPage` 由 **0** 变回 **1**；切回修后写法，三个读数逐字复原。
      破坏是在 `scripts/` 下的 **dot 前缀临时副本**里用 `process.env.RAW` 开关做的（不是就地改生产脚本），
      跑完即删——`ls -a scripts/ | grep '^\.tmp'` 为空。
      故「89 不是扫描器空转、90 也不是历史遗留」两个方向都有实测：**同一份 `src`，只有这一处开关不同**。

      📌 **一处自证过的假阴性教训（比缺陷本身更值得记）**：量为「注释到底影响几条规则」时，
      我连改三次补丁都读出「R3 纹丝不动」，一度据此写下「注释不影响 R3」。
      真相是**锚字符串选错了**：`const { code }` 与 `= readSource(file)` 这两行在 **R2 与 R3 里逐字相同**，
      `String.prototype.replace` 命中的是**先出现的 R2**，R3 根本没被改到——
      **探针没改到目标，却给出「无差异」的读数**，而"无差异"正是我当时想验证的结论，极易被采信。
      改为从 `s.indexOf('function rule3()')` 之后切片再替换，读数立刻变成 89。
      同一形状的坑本文件在 T041 已记过一次（探针边界），此处是第二例：
      **「改完没变化」必须先证明「改动真的落到了目标上」，再谈结论。**

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
