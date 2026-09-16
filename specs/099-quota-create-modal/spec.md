# 099-quota-create-modal：配额创建表单改成列表页内弹窗

**Created**: 2026-09-16
**状态**: 实施中（立项阶段先登记，**勾选框不预勾**）
**形制**: **界面形态改造类**——**不改端点、不改 DTO、不加迁移、不加权限码、不新增门禁脚本**。
唯一的生产改动集中在**一个页面文件**（`frontend/src/pages/quotas/QuotaListPage.tsx`）+ **一次删除**（`QuotaCreatePage.tsx` 与它的那条路由）。
**上游**: `specs/078-sales-quota`（FR-Q01 三级配额创建）—— 本项**粗化它的界面形态**，**不改它的任何 FR/SC**
**用户原话（2026-09-16）**: 「**创建配额表单，改成弹窗模式**」

---

## 1 由来

`/quotas/create` 是一个**独立路由页**：`frontend/src/pages/quotas/QuotaCreatePage.tsx`（126 行 = 返回按钮 + `<Title>` + 一张 `Card` + 一个 6 字段 `<Form>`），
唯一入口是列表页工具栏上的 `navigate('/quotas/create')`（`QuotaListPage.tsx:172`）。

而本仓自 088 起已有**共享弹窗表单原语** `frontend/src/components/ui/FormModal.tsx`（5 个页面、9 处使用），
全站惯例是**表单内联在列表页里**（`<Modal>`/`<FormModal>` 承载表单的写法共 55 处）：
**本仓没有任何 per-entity 的 `XxxFormModal.tsx`**（全库唯一的实体级抽取弹窗 `LeadConvertModal.tsx` 是因为被**两处**复用）。
⇒ 「为一个表单单开一个路由页」在本仓是**少数派写法**，而它带来的成本是实打实的：
返回按钮、`navigate` 往返、页面级栅格参数（`maxCols`）、以及**一份只有一处用到的路由**。

### 1.1 与 `078-sales-quota` 的关系（谁被谁改）

078 的 **FR-Q01**（三级配额创建）**没有**规定创建动作长在页面上还是弹窗里；
078 全文唯一的「弹窗」字样出现在 `tasks.md` 的配额**调整**弹窗条目（T049），
即 **078 未把创建页的形态钉死为独立页面** ⇒ 本项落在 078 的射程**之内**，是对界面形态的**收窄**，不是对需求的改写。
**078 的工件一字不动**；本项在 `research.md` §1 留档这层关系。

### 1.2 三条决定形状的实测事实

**① 共享原语 `FormModal` 对 `check-ui.mjs` 的 R2 规则是「隐形」的（这条决定了选型）。**
`scanTagEvents(code, 'Modal')`（`frontend/scripts/check-ui.mjs:97-118`）用 `<Modal` 作针，并要求后一字符**不是** `[A-Za-z0-9_.]`
（这条守卫是为了让 `<Form` 不命中 `<FormItem`）。`<FormModal` 里**根本没有** `<Modal` 这个子串 ⇒ **R2 看不见它**。
所以：用共享原语**天然过 R2**、且 `CANDIDATE_READINGS.R2 = 55`（`:782`）**一字不动**；
反过来手写裸 `<Modal>` + `<Form>` 则会让该候选数 55→56（**只印不判**）并**必须**显式写 `width`。
⚠️ 这条是**机制级**的结论（读了实现），不是「跑一遍没红」的观察 —— 见 `research.md` §2.1。

**② 删掉这条路由**不动任何机器计数**（已逐条核过判据，不是「跑一遍没红」）**。
`<Route path="quotas/create" .../>`（`App.tsx:879`）是**双引号、且没有 `name:`**，
而 `check-i18n.mjs` 的路由枚举正则要求**单引号 + 同一行有 `name:`**（`:176`）⇒ 它**从来没被那个「58 条」数过**；
`/quotas/create` 不在后端 `MENU_TREE` 里 ⇒ `check-menu.mjs` 的 **56** 不变；
全文无 `PERMS.` 引用、`constants/permissions.ts` 无 `quota:*` ⇒ `check-perms.mjs` 的 **68** 不变；
`App.tsx` 的 `ZH_ALLOWED` count **55**（53 处路由 `name:` + 2 处真欠账）也不变。
只有两个**只印不判**的数会动：`check-ui` 的扫描文件数 272→271、`check-zh` 的 269→268。
⚠️ **反例（不许碰）**：`App.tsx` 里 `{ path: '/quotas', name: '销售配额' }` 那条一删，
`check-i18n` 的 `orphanKeys` 与 `check-zh` 的第三分支会**同时转红**。

**③ 顶部三张 KPI 汇总卡不会跟着表格刷新（本项最容易出的真缺陷）。**
`quotaApi.getSummary(year)` 的 `useEffect` 依赖数组只有 `[year]`（`QuotaListPage.tsx:19-24`），
而 `actionRef` 虽已声明（`:15`、`:137`）却**从未调用过 `reload()`**。
⇒ 「提交后刷新」这件事**必须显式加一条通路**，否则新建的配额只出现在表格里、顶部汇总停在旧数上。

---

## 2 用户故事

### US1 创建配额不再离开列表页（P1）

**角色**：录入配额的销售管理员。**诉求**：点「新建」、填完、提交，**留在原地看着列表更新**，而不是被弹到一个独立页面、再点返回回来。

**为什么**：独立页把「上下文」切断了 —— 列表页的年份筛选、KPI 汇总、翻页位置都在背后；
而这条路径实际只做一件事（填 6 个字段后 POST 一次），不值得一次完整的页面导航。
**验收**：① 工具栏「新建」打开弹窗（`lg` 档 800px），**6 个字段、3 条 `required` 校验、全部文案键逐字不变**；
② 提交成功后**弹窗关闭**、**表格重拉**、**顶部三张 KPI 卡重取**；③ 失败时弹窗**保持打开**并给出错误提示。

### US2 旧的独立页与它的那条路由不再存在（P1）

**角色**：下一个改配额模块的人。**诉求**：不要有两份「创建配额表单」。

**为什么**：两份实现要同步维护，而它们**必然分叉**（改一处忘一处），且没有门禁看着这一点。
**验收**：`QuotaCreatePage.tsx` 与 `App.tsx` 里那条 `<Route>` 一并删除，二者**在同一次提交**里完成；
`grep -rn "quotas/create" frontend/src frontend/e2e` **零命中**。

### US3 覆盖缺口不被「改完就走」掩盖（P2）

**角色**：接手这条链路的下一个人。**诉求**：知道改动的**回归证据是什么、不是什么**。

**为什么**：`QuotaCreatePage` **零单测**、`frontend/e2e/` **没有任何用例走创建流程**（配额只被导航型用例访问，从不填表、从不提交）。
⇒ 本项的行为层证据**只能来自新写的单测**；而 e2e 里那条 `'/quotas/create'` 在删路由后会**变成空真**（落到 `NotFoundPage` 却多半仍绿）。
**验收**：① 新增 `QuotaListPage.form.test.tsx`（5 组断言）；② e2e 的 `FORM_PAGES` **摘掉该条**并给注释加**带日期 ⚠️ 订正**；③ 上述缺口在 `research.md` 里**照实登记**，不修饰。

---

## 3 功能需求

### 3.1 弹窗本体

- **FR-001** `frontend/src/pages/quotas/QuotaListPage.tsx` 内联一个创建弹窗，外壳用**共享原语** `FormModal`
  （`frontend/src/components/ui/FormModal.tsx`，从 `../../components/ui` 引入），**档位 `size="lg"`（800px）**。
  **不手写裸 `<Modal>`**（理由见 §1.2 ①：共享原语既是 R2 的合规路径，也自带 Enter 提交 / `destroyOnClose` / `confirmLoading` / 默认 `cancelText`）。
- **FR-002** 弹窗内**逐字搬运**原表单：**同样 6 个字段**（`year` / `quarter` / `teamId` / `userId` / `amount` / `periodRange`）、
  **同样的控件与范围**（`InputNumber min=2000 max=2100`、`Select` Q1–Q4、`min=0.01 step=0.01`、`DatePicker.RangePicker`）、
  **同样的 3 条 `required`**，**同样的 `initialValues={{ year: new Date().getFullYear() }}`**。
- **FR-003** 弹窗 `title` = `pages.quotaCreate.title`；`okText` **显式传** `pages.quotaCreate.btnCreate`
  （把原提交按钮上的「创建」搬到弹窗主按钮 ⇒ **用户可见措辞逐字不变**）；
  取消键**不传**，吃 `FormModal` 的默认值 `common.button.cancel`（原页面的取消按钮用的就是这一个键）。
- **FR-004** **保留** `layout="vertical"` 与 `<FormGrid minItemWidth={VERTICAL_MIN_ITEM_WIDTH}>`；
  **删掉 `maxCols={3}`** —— `FormGrid` 的文件头明说 `maxCols` 是给**页面级宽容器**用的（`:63-65`），
  弹窗是 480–960 的窄容器，`auto-fit` 按容器宽度自然排 2 列，**不需要任何"哪档配几列"的约定**。
- **FR-005** 工具栏「新建」按钮改 `onClick={() => setModalOpen(true)}`，**不再 `navigate`**；
  按钮上的 `pages.quotaList.btnCreate` 与图标**不变**（列表页的按钮文案不动）。

### 3.2 提交通路与刷新

- **FR-006** `FormModal` 的 `onSubmit` 内：`await form.validateFields()` → 组 `SalesQuotaRequest`
  （`periodStart`/`periodEnd` 由 `values.periodRange[0]/[1].format('YYYY-MM-DD')` 得出，**与今天逐字相同**）→ `quotaApi.create(payload)`。
- **FR-007** 成功路径：`message.success(t('pages.quotaCreate.msgCreated'))` → `setModalOpen(false)`
  → `actionRef.current?.reload()`（**表格重拉**）→ `setRefreshToken(n => n + 1)`（**KPI 重取**）。
  ⚠️ **两条刷新是两个动作、必须都被观测到**：只做表格那条，`getSummary` 不会重取（§1.2 ③）。
- **FR-008** KPI 重取的通路：新增 `refreshToken` state 并把它**加进那个 `useEffect` 的依赖数组**
  （最小改动，**不引入第二套取数路径**、不改成 `useCallback`+手动调用）。
- **FR-009** 失败路径：`catch` 里 `message.error(t('pages.quotaCreate.msgCreateFailed'))`，
  **弹窗保持打开**、**不 rethrow**。
  ⚠️ 理由写进代码注释：`FormModal` 的 `handleOk` 是 `try/finally` **没有 `catch`**，
  rethrow 会造出一个**无人接管的 promise rejection**（`CustomerListPage.tsx:125-126` 有同一条注释）。
- **FR-010** 消息 API 用 `App.useApp()` 取（`const { message } = App.useApp()`）—— 本仓 69 个文件走这条，是主流写法。

### 3.3 旧页面与旧路由的删除

- **FR-011** 删除 `frontend/src/pages/quotas/QuotaCreatePage.tsx`（整文件）。
- **FR-012** `frontend/src/App.tsx` 删除该页的 `lazy` import 与 `<Route path="quotas/create" ...>`。
- **FR-013** ⚠️ **FR-011 与 FR-012 必须与 FR-001 在同一次提交里**：分两次会让中间提交留下「指向已删文件的路由」，`typecheck`/`build` 会红。

### 3.4 i18n

- **FR-014** **复用既有 `pages.quotaCreate.*` 键**（该命名空间实测 **17 键**：`title` / `btnBack` / `formYear` / `msgYearRequired` / `formQuarter` / `phQuarter` /
  `formTeamId` / `phTeamId` / `formUserId` / `phUserId` / `formAmount` / `msgAmountRequired` / `formPeriod` / `msgPeriodRequired` /
  `btnCreate` / `msgCreated` / `msgCreateFailed`），弹窗里继续用其中 **16 个**，**键名与值一字不改**。
- **FR-015** **删除死键 `pages.quotaCreate.btnBack`**（弹窗没有返回按钮）：`zh-CN.ts` 与 `en.ts` **同批删**。
  ⚠️ 只删一侧会在 `check-i18n.mjs` 的**双向键集合比对**处转红（这是机器强制的「双语同批」）。
  ⚠️ **零新增键** ⇒ 本项**不改** `PROJECT_FEATURES.md` 里 i18n 的**新增键**叙述，只改**键数/行数的现值**（FR-018）。

### 3.5 用例

- **FR-016** 新增 `frontend/src/pages/quotas/QuotaListPage.form.test.tsx`（命名照 088 的 `<Page>.form.test.tsx` 先例），**5 组断言**：
  1. 点「新建」⇒ 弹窗开、标题键在、`cancelText` 吃到默认值 `common.button.cancel`、**宽度是 `lg` 档 800**（读 `.ant-modal` 的内联 `style`，**照 `CustomerListPage.form.test.tsx:132` 的既有读法**，不用 `getComputedStyle`）；
  2. 必填留空点保存 ⇒ **字段级错误出现** **且** `quotaApi.create` **未被调用**（**只断「没调用」是假绿**）；
  3. 填全提交 ⇒ `create` 收到正确 payload（**含 `YYYY-MM-DD` 的日期格式化**与 `year` 默认值）；
  4. 成功后 ⇒ 弹窗关闭 + **表格重拉** + **KPI 重取**（**两条分开断言**）；
  5. 失败 ⇒ 弹窗**保持打开**（不许静默关掉）。
- **FR-017** e2e `frontend/e2e/module-page-auth.spec.ts`：`FORM_PAGES` **摘掉 `'/quotas/create'`**；
  该文件里以它为「纯表单页」例子的**注释原文保留 + 追加带日期 ⚠️ 订正**（换一个仍存在的例子或改为泛指）。

### 3.6 登记与订正（一个数字住在好几个地方 ⇒ 一起改）

- **FR-018** `PROJECT_FEATURES.md`：① **Spec 模块行** 97 → **98（001–099，缺 069）**；
  ② **i18n 资源行的现值**（行数 + 键数）按**实跑读数**改写（删 1 键 ⇒ 键数 2963 → 2962，**以 `i18n:check` 的打印为准，不推算**）。
- **FR-019** `specs/README.md` 模块表加 099 行（**状态列如实写「⏳ 进行中」**，**不预勾**）；编号说明纳入 099；`README.md:163` 的 spec 计数 97 → **98**。
- **FR-020** `specs/roadmap.md`：`最后更新` / `整体覆盖度`（`001–098` → **`001–099`**）/ `## 当前进度` 加 099 行（**勾选框留空**）。
  ⚠️ 两条聚合数的旧值**原文逐字保留 + 带日期 ⚠️**，判据写 `ls -d specs/[0-9]* | wc -l`。
- **FR-021** **订正不静默**（原文逐字保留 + 带日期 ⚠️，粒度到每一列）：
  ① `PROJECT_FEATURES.md` 记述「新增 `QuotaCreatePage` 与 `/quotas/create` 路由」的那一段；
  ② `specs/083-engineering-consolidation/quickstart.md` 的 14 条必访路由清单（含 `/quotas/create`）⇒ 注明该路由已移除、改访 `/quotas` 并点「新建」。
- **FR-022** **历史读数一字不动**：`specs/088-frontend-layout-consistency/tasks.md` 与 `specs/090-list-page-fill-height/{spec,plan,tasks}.md` 里
  以 `/quotas/create` 为**几何/宽度取样点**的实测（含 `.page-fade [101,713]` 那条）是**写下时的真实读数**，**照旧有效**。
  ⇒ 本项只在 `research.md` §4 **如实披露**「此后这些基线失去对象」，**不改写历史**。

---

## 4 非目标（明确不做，且各有理由）

1. **不给创建按钮加前端权限码**：后端 `SalesQuotaController` 有 `@RequirePermission("quota:create")`，
   而前端 `constants/permissions.ts` 里**没有 `quota:*` 任何码**、按钮至今对任何能看到 `/quotas` 菜单的用户都渲染（点了才可能 403）。
   这是**既有空档**，与本项无关 ⇒ **只在 `research.md` §5 登记，不修**。
2. **不动那 6 个字段的语义、校验规则、文案与顺序** —— 本项只换**容器**，不换**表单内容**。
3. **不把其余 4 个配额子页（分解/达成/版本/对比）弹窗化，也不给它们补入口**：
   核实到列表页**没有行操作列**，那三页在 UI 里**没有任何入口**（只能直接输 URL）——**既有缺口**，**登记在 `research.md` §5**，不在本项扩张。
4. **不改 `078-sales-quota` 的任何 FR/SC 与工件**（本项不改需求，只收窄形态）。
5. **不改 `088`/`090` 里以该页为取样点的历史读数**（带快照日期的实测照旧有效）。
6. **不把表单抽成 `components/**/QuotaCreateModal.tsx`**：本仓没有 per-entity 弹窗组件的先例，
   且 `src/components/**` 被 `check-ui.mjs` 的 **R7（孤儿组件）**看着 —— 为一个只有一处调用的表单引入新文件不划算。
7. **不碰后端、迁移、`.specify/feature.json`**；**不跑任何 `/speckit-*` 命令**（工件手写）。
8. **不顺手清 `App.tsx` 的 2 处真欠账**（098 已点名留给后续批次）。

---

## 5 成功判据

- **SC-099-001（弹窗与今天的表单等价）**：弹窗里**6 个字段 / 3 条 `required` / 全部文案键**与删除前的 `QuotaCreatePage` 逐项对得上；
  `okText` 是 `pages.quotaCreate.btnCreate`、`cancelText` 是默认的 `common.button.cancel`、宽度是 `lg` 档 **800**。
- **SC-099-002（两条刷新各自有牙）**：提交成功后**弹窗关闭** + **表格重拉** + **KPI 重取**；
  **D3（删 `reload()`）与 D4（删 `setRefreshToken`）分别做破坏、分别观测到转红** —— 合起来做会掩盖「KPI 不刷」。
- **SC-099-003（旧实现清零可核）**：`grep -rn "quotas/create" frontend/src frontend/e2e` **零命中**；
  `QuotaListPage.tsx` 里 `navigate('/quotas/...')` 只剩**对比页**那一条；`pnpm build` 通过。
- **SC-099-004（门禁不倒退）**：八道门禁 + `pnpm build` 逐条 exit 0；
  `ui:check` 的冻结台账**未增长**（**以实跑读数为准**）、`R2` 读数仍为 **55**（用共享原语 ⇒ 候选池不变）；
  `zh:check` 的命中与台账**均不变**（`App.tsx` 的 55 不动）；`i18n:check` 的两侧键数**相等**且 = **2962**（实测）。
- **SC-099-005（三个数字落点一致）**：`i18n:check` 的键数、`PROJECT_FEATURES.md` 的 i18n 行、`specs/README.md` 的 099 行 **三处相同**；
  Spec 模块数在 `PROJECT_FEATURES.md`、`specs/README.md`、`README.md:163`、`roadmap.md` 的覆盖度行 **四处一致**。
- **SC-099-006（订正不静默）**：`PROJECT_FEATURES.md` 配额段的原文、`083/quickstart.md` 的 `'/quotas/create'` 那一行、
  `roadmap.md` 的 `001–098`、`README.md:163` 的 `001~098` **全部仍可 grep 到**（零命中 = 静默改写，须逐条确认非零）。
- **SC-099-007（不留空真）**：e2e 的 `FORM_PAGES` 里**不再有**已不存在的路由；
  且**如实写明**：`pnpm test:e2e` **未跑整套**（理由见 §6）。
- **SC-099-008（覆盖率与阈值）**：前端覆盖率四项 **≥ 33.6 / 47.2 / 21.4** 且**阈值未下调**；**不与上次的小数位比**（Branch 差 0.01 照 098 先例单列一节写明）。

---

## 6 未验证边界（如实声明）

- **没有门禁看着「`navigate()` 的目标路由是否存在」**：本项**实测**了这一空档（破坏 D5 把 `navigate('/quotas/create')` 写回按钮，
  `typecheck`/`lint`/`build` **全绿**）⇒ 替代判据是 **grep 零命中**（SC-099-003）**加一次只读冒烟**（打开 `/quotas` 点「新建」看弹窗开）。
  **不得**声称「删干净了」这件事被任何门禁保证过。
- **写库的端到端验证不做（除非用户明确同意）**：提交会往**共享开发库**写入一条配额。
  按仓规**未经明确同意不得对共享库做任何操作** ⇒ 本项默认只做**只读冒烟**（开弹窗、填字段、看校验、**不点提交**）；
  若要做写库验证，则照既有配方起**隔离实例**（临时端口 + 独立 schema + 另一个 Redis db），收尾 `DROP`/`REVOKE`/`FLUSHDB` 并核对共享库未动。
- **e2e 只跑一条、不跑整套**：`module-page-auth.spec.ts` 是**唯一**被本项直接影响的 e2e 文件（它是**导航型**用例，不填表、不提交）；
  其余 spec 可能写共享库 ⇒ **不擅自跑整套**。若因故没跑，**如实写明「未跑 e2e」**，不得用门禁全绿冒充端到端证据。
- **`QuotaCreatePage` 从来没有过单测、e2e 也从未走过创建流程** ⇒ 本项的**唯一行为层回归证据是新写的 5 组单测**；
  它覆盖的是**页面层**（弹窗开关、校验、payload、刷新），**不等于**端到端（后端真实落库、权限码 403 路径）被验证过。
- **`quota:*` 前端权限码缺失、4 个配额子页无 UI 入口、`App.tsx` 的 2 处真欠账** —— 均**只登记不改**（`research.md` §5）。
