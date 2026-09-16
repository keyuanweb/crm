# 研究报告：配额创建表单改成列表页内弹窗（099）

**日期**：2026-09-16 ｜ **方法**：只读勘察（读实现 + 实跑只读门禁），**未改动任何文件**
**结论先行**：本项是**一次界面形态收窄**——把「为一个表单单开一个路由页」换成全站主流的「列表页 + 表单弹窗」。
下面六节分别是：与 078 的射程关系、选型所依据的**机制级**事实、逐门禁的删除敏感性、失去对象的历史基线、三条**既有空档**登记、以及**覆盖缺口**的如实交代。

---

## 1 与 `078-sales-quota` 的射程关系（谁改谁）

| 078 的条目 | 原文要点 | 本项是否触碰 |
|---|---|---|
| **FR-Q01** | 三级配额创建（年度 / 季度 / 月度） | **不触碰**——本项不改「能建几级」，只改「在哪建」 |
| **FR-Q02 / FR-Q03** | 逐层分解；总和一致性（误差 ≤ 0.01） | 不触碰 |
| **SC-Q01** | 「配额分解流程可在 3 步内完成」 | **不受影响**——弹窗把「进页面 → 填 → 提交 → 返回」压成「填 → 提交」，步数**只减不增** |
| **SC-Q05** | 「前端 typecheck/lint/build 通过」 | **被沿用**（本项把 `pnpm build` 也纳入门禁，与它同口径） |
| `tasks.md` 的 T049 | 「创建前端配额**调整**页面/Modal（调整金额 + 原因输入）」 | **不是同一件事**——那是**调整**（`PATCH` 语义），本项改的是**创建** |

**判定**：078 全文**没有**任何一条把创建动作钉死为「独立页面」；全 078 唯一的「弹窗」字样就是上面那条 T049（且它说的是**调整**）。
⇒ 本项落在 078 的射程**之内**，是对**界面形态**的收窄，**不是**对需求的改写。
**078 的工件一字不动**（`spec.md` / `plan.md` / `tasks.md` / …全部保持原样）。

---

## 2 选型依据：三条**机制级**的事实

### 2.1 `check-ui.mjs` 的 R2 对 `<FormModal>` **隐形**（本项最重要的实现级发现）

R2 的判据（`frontend/scripts/check-ui.mjs:630-649`）：**承载表单的 `Modal` 必须显式定宽**——
「承载表单」= 「**该 Modal 的区域**里出现真实的 `<Form` 标签」。而「Modal 的区域」由 `scanTagEvents(code, 'Modal')` 定位，
该函数的针是字面量 **`<Modal`**，并且有一道**整体匹配守卫**：标签名后一个字符若匹配 `[A-Za-z0-9_.]` 就丢弃该命中（`:114-118`）。

```js
const openNeedle = `<${tagName}`            // '<Modal'
if (after !== undefined && /[A-Za-z0-9_.]/.test(after)) { … continue }   // 防 `<Form` 命中 `<FormItem`
```

**`<FormModal` 里不包含子串 `<Modal`**（它是 `<FormModal`，`<` 后面直接是 `F`）⇒ **R2 看不见任何 `<FormModal>`**。
三个可核推论：

1. 用共享原语 ⇒ **天然过 R2**，且 `CANDIDATE_READINGS.R2 = 55`（`:782`）**一字不动**（这个 55 就是「承载表单的裸 `Modal`」数——5 个页面、9 处 `<FormModal>` **不在**其中）。
2. 手写裸 `<Modal>` + `<Form>` ⇒ 候选 55→**56**，且**必须**显式写 `width`（`width` 缺省即命中 R2）。
3. **这不是「风格偏好」而是规则实现的直接后果**：`R2` 的注释原文就写着「（或改用 `FormModal`）」——原语是**被指定**的合规路径。

⚠️ 因此本项**不**把「用原语更优雅」当理由；理由是**它就是 R2 承认的那条路**，且破坏 **D7**（换成裸 `<Modal>` 且不写 `width`）会让 R2 当场转红。

### 2.2 弹窗表单在本仓是**主流**，而抽取成组件**没有先例**

| 族 | 计数 | 出处 |
|---|---|---|
| 裸 `<Modal>` + 内联 `<Form>` | **55**（`CANDIDATE_READINGS.R2`） | `check-ui.mjs:782`（2026-09-15 实测） |
| 共享原语 `<FormModal>` | **5 个页面 / 9 处**：`TagListPage`(1)、`CustomerListPage`(2)、`InvoiceListPage`(2)、`ProductListPage`(1)、`PersonalCenterPage`(3) | 全仓 grep `FormModal` |
| per-entity 弹窗组件（`XxxFormModal.tsx` / `XxxForm.tsx`） | **0** | 全库 `*Modal*.tsx` / `*Form*.tsx` 只命中 `components/ui/FormModal.tsx`（原语）、`components/LeadConvertModal.tsx`（**唯一**的实体级抽取，因被 `LeadListPage` 与 `LeadDetailPage` **两处**复用）、以及 `OnlineFormPage.tsx` / `PublicFormPage.tsx`（是**页面**不是弹窗） |

⇒ 「内联在列表页里」是惯例；**抽组件需要理由（复用），本项没有**（只有一处调用）。
且 `src/components/**` 被 **R7（孤儿组件）**看着（`R7_ALLOWED = []`）——抽出去而没接好就会红。

### 2.3 `FormGrid` 的 `maxCols` 是给**页面级**容器用的

`components/ui/FormGrid.tsx:63-65` 原文：**「页面级表单要传它**：弹窗宽度是 480–960 的窄容器，auto-fit 的列数天然合理；
而页面级容器约 980px……那时『容器驱动』就不再是优点而成了漂移。弹出档位由 088 T043 裁决为 **3**」。
⇒ 该参数**存在的理由**就是「容器太宽」；把它搬进 800px 的弹窗，**它守护的那个问题不存在了** ⇒ 本项**删掉 `maxCols={3}`**。
（`minItemWidth={VERTICAL_MIN_ITEM_WIDTH}` **保留**：那是纵向布局的既有下限，与容器宽窄无关。）

---

## 3 逐门禁的删除敏感性（**读判据**得出，不是「跑一遍没红」）

被删的那行是 `frontend/src/App.tsx` 的 `<Route path="quotas/create" element={<QuotaCreatePage />} />`。

| 门禁 | 判据（读实现） | 删后 | 机制说明 |
|---|---|---|---|
| `check-i18n.mjs` | `:176` 的路由枚举正则：**单引号**的 `path: '…'` + 同一行有 `name:` | **58 不变** | 那行是**双引号**且**无 `name:`** ⇒ 它**从来没被这条规则数过**；`:233` 只**打印** 58，**没有任何断言**看着这个数 |
| `check-menu.mjs` | `:16` 从后端 `RoleConstants.java` 的 `MENU_TREE` **现场重生成**再逐字节比对（`:37-40`） | **56 不变** | `/quotas/create` 不在 `MENU_TREE`；只有动 Java 才需要 `pnpm menu:gen` |
| `check-perms.mjs` | `:238` 数 `permissions.ts` 的条目 = **68**；`:265-284` 核对 `PERMS.xxx` 引用 | **68 不变** | 该页全文**无** `PERMS.` 引用；`permissions.ts` 里**没有 `quota:*` 任何码**（见 §5.1） |
| `check-zh.mjs` | `:190-241` 的 `ZH_ALLOWED` 4 条，其中 `App.tsx` **count 55**；`:325-340` 第三分支双向校验 | **55 不变** | 那行**没有中文字面量**；文件数只**印**不判（**269→268**） |
| `check-ui.mjs` | `:82` 文件数、`:536-546` `Form.Item` 计数**都只印不判**；`:750-768` `MIN_CANDIDATES`（R4/R5 的下限是 **1**，实跑候选 148/149） | 只印的数变：**272→271**、**304→298** | 删掉的那页含 **6 个 `Form.Item`**，全部合规；规则候选远高于下限 |

⚠️ **反例（本项最该防的误伤）**：`App.tsx` 里 `{ path: '/quotas', name: '销售配额' }` 那条**必须留**——
删它会让 `check-i18n` 的 `orphanKeys`（`:178-180`，清单项在路由里找不到）与 `check-zh` 的第三分支（`App.tsx` 命中 55→54）**同时转红**。

---

## 4 失去对象的历史基线（**不改写历史**）

以下读数都是**写下时的真实实测**，本项**一字不动**；此处置只是**披露**：这一类基线从此**没有可复验的对象**。

| 落点 | 原文内容 | 本项处置 |
|---|---|---|
| `specs/088-frontend-layout-consistency/tasks.md`（`:815`） | 表格登记 `QuotaCreatePage` / `/quotas/create` / 容器宽 **1000** / 6 个子项 / 字段名 | **一字不动** |
| 同文件 `:849` | 「`/quotas/create` 等 **5 个宽页**」的实测判据 | **一字不动** |
| `specs/090-list-page-fill-height/spec.md`（`:161`、`:198`） | SC-004 把 `/quotas/create` 列为「非列表页几何逐像素一致」的**四个取样页之一** | **一字不动** |
| `specs/090-list-page-fill-height/tasks.md`（`:71`） | parity 基线：`/quotas/create` 的 `.page-fade [101,713]` | **一字不动** |
| `specs/090-list-page-fill-height/plan.md`（`:79`） | 加两个「不该变」的页面，含 `/quotas/create` | **一字不动** |
| `specs/083-engineering-consolidation/quickstart.md`（14 条必访路由清单） | 清单含 `/quotas/create` | ⚠️ **这是活清单** ⇒ 原文保留 + 带日期 ⚠️ 订正（改访 `/quotas` 并点「新建」） |
| `PROJECT_FEATURES.md` 配额段 | 记述「新增 `QuotaCreatePage` 与 `/quotas/create` 路由」 | ⚠️ **同上**：原文保留 + 带日期 ⚠️ 订正 |
| `frontend/e2e/module-page-auth.spec.ts` | `FORM_PAGES` 含 `'/quotas/create'`；`:143-145` 注释以它当「纯表单页」例子 | ⚠️ **这是活判据** ⇒ 摘掉该条 + 注释带日期 ⚠️ 订正 |
| `CRM_FEATURE_COMPARISON.md` / `specs/078-sales-quota/**` | 本项勘察**未发现**以该页为判据的条目（078 唯一相关条目是 T049 的**调整**弹窗） | 不动 |

**判据（写清以免被误读）**：区分「**带快照日期的实测读数**」（照旧有效，不改）与「**活清单/活判据**」（必须同步）——
前者是历史，后者是**当前**的验收依据。⚠️ 也**不据此声称**这些历史基线「作废」：它们是**当时**成立的证据，只是**今天没有对象可复验**。

---

## 5 既有空档登记（**只登记，不修**）

### 5.1 前端没有接 `quota:create` 权限码

后端有门：`SalesQuotaController` 上 `@RequirePermission("quota:create")`（调用于创建端点）；
权限字典里 **6 个配额码**齐备（`quota:read` / `quota:create` / `quota:update` / `quota:delete` / `quota:breakdown` / `quota:achievement`）。
而前端：`constants/permissions.ts` 里 **`quota:*` 零命中**，`pages/quotas/` 下 `usePermission|hasPerm` **零命中**。
⇒ **创建按钮对任何能看到 `/quotas` 菜单的用户都渲染**，点了才可能被后端 403。

⚠️ 这与 `permissions.ts` 自己的注释（「**刻意只登记前端真的用它做 gating 的码**」）**一致**，所以它**不是错**，而是**未接线**。
**本项不修**（加码 = 改权限语义，属另一件事）；**在此登记**，免得下一个人以为「有权限码看着这个按钮」。

### 5.2 四个配额子页在 UI 里**没有入口**

`QuotaListPage` 的 `columns` **没有行操作列**，全仓**唯一**跳转到其它配额页的调用点是工具栏的「对比」按钮（`navigate('/quotas/comparison')`）。
⇒ `/quotas/:id/breakdown`、`/quotas/:id/achievement`、`/quotas/:id/versions` 三页**只能直接输 URL 到达**。
**本项不修**（补入口属新功能，且要决定「从哪一行进去」）；**在此登记**。

### 5.3 **没有门禁看着 `navigate()` 的目标是否存在**

本项**实测**（破坏 D5）：把 `navigate('/quotas/create')` 写回按钮后，`typecheck` / `lint` / `build` **全绿**——
因为路由是**字符串**，类型系统与打包器都**不校验它指向哪**。
⇒ 「删干净了」这件事**不能**靠任何门禁证明，替代判据是 **grep 零命中**（SC-099-003）**加**一次**只读冒烟**。
⚠️ **如实披露**：这是本项**已知的**验证空档，**不声称**它被护栏覆盖。

---

## 6 覆盖缺口（决定本项的证据形态）

| 事实 | 后果 |
|---|---|
| `QuotaCreatePage.tsx` **零单测**（`src/pages/quotas/` 下无 `*.test.tsx`） | 删除它**不会**让任何用例转红 ⇒ 删除动作本身**无回归证据**，只能靠 grep + 冒烟 |
| `frontend/e2e/` **无任何用例走创建流程** | `module-page-auth` 只 `goto` + 断言「无 401 / 非白屏」（`:133-148`）；`geometry-list-page` 只测 `/quotas` 的几何；`role-permissions` 只 `goto('/quotas')` ⇒ **没有**「打开 → 填表 → 提交」的端到端用例 |
| `quotaApi` 的单测只断**凭据头**与**响应解包层数**（`services/api/moduleApiClients.test.ts:66,98`） | `quotaApi.create` 的**请求体**从来没有被断言过 ⇒ 本项的单测 3 是它的**第一处** payload 断言 |
| 单测环境把 `react-i18next` mock 成 `t(key) → key`（`src/test/setup.ts`） | 断言只能断**键名**；「文案逐字不变」这件事**在单测层不可证**（要 e2e 或人工看） |

⇒ 本项新增 **5 组**行为层用例是本项**唯一**的行为回归证据；**不声称**它等价于端到端验证（见 `spec.md` §6）。

---

## 7 结论

1. **改动面**：1 个页面文件（加弹窗）/ 1 个文件删除 / 1 个 i18n 键删除 / 1 条路由删除 / 1 个 e2e 清单条目 / 1 个新测试文件 / 4 处文档登记与订正。
2. **风险最高的两条**：**KPI 不刷新**（§2.3 的后果，门禁抓不到 ⇒ 靠 D4 与单测 4 看着）、**`navigate` 目标无校验**（§5.3，只能靠 grep + 冒烟）。
3. **没有**任何一条「因为难改所以不动」的豁免；本项的每一条「不做」都附了可核依据（`spec.md` §4）。
