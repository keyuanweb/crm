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
- [ ] **T022** `.github/workflows/ci.yml` 加一步 `ui:check`。**⚠️ 受阻，未完成。**
      该文件当前被**并行会话 `engineering-consolidation-ci-gates` 持有未提交**（连同 `Dockerfile`、`backend/pom.xml`），
      改动它会踩进对方的在飞工作。**需用户裁决**：等对方提交后由我补，或转告对方一并加上。

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
- [ ] **T035** `products/ProductListPage.tsx` —— `FormModal` + `FormGrid`，覆盖 `editing &&` 条件子区块（`:302-340`）。
- [ ] **T036 **[P] `customers/CustomerListPage.tsx` —— 外壳。**业务价值最高**（销售每天用）、
      `Form.Item` 最多（9 个）、唯一带自定义字段 + 第二个（纵向）转移弹窗的页面。测试耦合 **0/0/6**（中）。
      **三条约束已逐行坐实，不得违反**：① `:154` 与 `:245`（`not.toHaveClass`）的 `toHaveClass('ant-btn-primary')`
      ⇒ **不得把视图切换的 `<Space>`/`<Button>` 组换成 `Segmented`/`Radio.Group`**；
      ② `:136` 的 `getByText('Acme 科技')` 与 `getByRole(..., {name:/viewPool/})` **重复渲染即抛错**；
      ③ `:133` 的 `queryAllByRole('checkbox')` 按数量断言 ⇒ **不得改动 `rowSelection`**。
- [ ] **T037** `customers/CustomerListPage.tsx` —— `FormModal` + `FormGrid`。
      **不在此提交里抽 `ImportResultModal`**（会改动弹窗正文，属测试可见的 DOM 变更，另起提交）。
- [ ] **T038** 视觉验收（**用户执行，SC-005**）：1920/1440/1024/768/**375** × 中英文，
      4 个页面的列表 + 弹窗 + 详情；并就 plan.md 末节的 **6 件事**给出裁决。

---

## P3：铺开（样板验收通过后）

> **按表单形态分批，不按业务模块分批**——这样每批是一次机械变换 + 一套验证。

- [ ] **T040** 3.1 其余 **3 个无宽度弹窗**（`InvoiceListPage` 与 `TagListPage` 已在 P2 收掉）：
      `announcements/AnnouncementPage.tsx`、`sla/SlaCalendarPage.tsx`、`visits/VisitListPage.tsx`。
      完成后把 **R2 从 warn 翻成 error** 并删掉已还清的条目。
- [ ] **T041** 3.2 其余 `layout="vertical"` 的表单（**43 个**）—— **密度收益最大的一批**
      （单列堆叠 → 两列栅格，表单高度直接减半）。
      ⚠️ 注意不要把"43 个纵向**表单**"与"58 个承载表单的**弹窗**"混为一谈（§2.2 与 §2.4 是两个分母）。
- [ ] **T042** 3.3 其余横向表单（**22 个**，全部已设 `labelCol`；把 `labelCol` 定宽改用 `useFormMetrics`）。
- [ ] **T043** 3.4 **13 个页面级表单**。
- [ ] **T044** 3.5 详情页（077 系 4 页 vs 传统系 `Descriptions` 11 个文件两套版式）。
- [ ] **T045** **R3 从 `--strict` 翻成 error**（96 处销账完毕后），并删除 P2/P3 期间已还清的全部白名单条目。

---

## P4：退役（与本批次相关的对抗性 CSS）

- [ ] **T050** 删 `index.css` 的 `.ant-btn-primary` `!important`（**单独一次提交**，让视觉差异可归因）。
- [ ] **T051** 删 `.ant-menu-item-selected` `!important`（同上）。
- [ ] **T052** 删 `.ant-pagination-item-active` `!important`（同上）。
- [ ] **T053** 清扫 **66 处** `borderRadius: 10` 字面量（改用 token）。
- [ ] **T054** 删 `src/components/ContactsCard.tsx`（真孤儿，0 引用）并删掉 R7 白名单条目。

---

## 附带发现（本规格途中发现，**不在本规格范围内修**）

| # | 发现 | 处置 |
|---|---|---|
| 1 | **规则 2 的真实基数是 25 处 / 19 文件，不是 plan 初稿的 23** —— 23 是**只看 `src/pages/`** 的口径，规则扫全部产品 tsx，多出 `components/FollowUpTimeline.tsx:141` 与 `components/LeadConvertModal.tsx:43` | 两个数都对，量的是不同范围；已记入 plan.md 与 research.md §8 |
| 2 | **规则 6 的 11 处构成与 research.md §2.9 不同**：§2.9 把 `title=` 也计入（`LoginPage` 记 2 处），实现只扫 `placeholder`/`aria-label`（`LoginPage` 记 1 处），差额由 `ScheduledExportCreatePage.tsx:115` 的 JSON 示例补上。**总数巧合相同，构成不同**；§2.9 的"6 处该翻译"应改为 **2 处**（两个 `aria-label`） | 已记入 research.md §9 |
| 3 | `vite.config.ts:45-56` 的覆盖率快照**已过期**（写的是"22 文件 / 96 用例"，实际已 72→76 文件），且 `specs/083-engineering-consolidation/data-model.md §4` 自己声明了同步义务 | **待刷新**（阈值一律不动，084 T037）；本规格结束时另起提交 |
| 4 | `.ai-card` **不是死代码——该选择器根本不存在**（`index.css` 里只有 `.ai-suggestion-card` / `.ai-icon-pulse`） | 已订正（research.md §4.1）；plan 初稿的「非目标」清单已删去该条 |
| 5 | `Descriptions layout="horizontal"` 的行号有两个口径：plan 引 `layout=` 所在行（317/357/217/299），脚本引**标签起始行**（313/353/213/295），差 4 行 | **统一用标签起始行**（唯一），已订正（research.md §4.3） |
| 6 | **`InvoiceListPage` 首屏从不拉统计**：`loadStats()` 只被 `reload()` 调用，而 `reload()` 只被 `onCreate`/`onVoid` 调用 ⇒ 首次进入页面时三个统计卡片恒为 `0% / 0.00 / 0.00`，只有开过票或作废过一张才变真值。换 `StatCard` **之前**即如此（同一个 `stats` state），与 T031 无关，但验收时极易被误读成"`StatCard` 把数字改坏了" | **已修**（T031 之后的第三次提交）：加挂载期 `useEffect`（照本仓 `useCallback`+`useEffect([load])` 惯例，避免 `exhaustive-deps` 警告）；`form.test` 首条断言从 `0%`/两个 `0.00` **翻成真实值**，且**实测过它真会红**（临时禁用 effect → 该用例失败、其余 4 条仍绿）。见 research.md §11.4 |
| 7 | **R3 的 96 处里至少有一处不能用 `FormGrid` 修**：`TagListPage.tsx:141` 的 `<Col key={c} span={2}>` 是颜色选择器的 **10 个色块**（全宽项），而 R3 的修复建议文本是"换成 `FormGrid` 或至少补断点"——对这一处**前半句是错的**（全宽项本来就该在栅格之外）。它确实有真实缺陷（320px 下每个色块约 20px），但正确修法是换成 `flex-wrap` 的色块行，属**结构变更** | **记给 T045**：R3 铺开时**不得机械替换**；本页在 T033 里保持原样，仍留在 R3 名单上（R3 计数 96 未变，是预期状态）。见 research.md §12.5 |
