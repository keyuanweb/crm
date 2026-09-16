# 实施计划：配额创建表单改成列表页内弹窗（099）

**上游**：[spec.md](./spec.md)（US1–US3 / FR-001–FR-022 / SC-099-001–008）
**用户裁决（2026-09-16）**：旧路由与页面**删除** ｜ 提交后**关弹窗 + 全刷新**（表格 **且** KPI）｜ `FormModal size="lg"`（800px）**保持纵向布局**

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### 一、契约优先的 API 设计（不可协商）
- [x] **后端一行不改**：无端点、无 DTO、无迁移、无权限码；`quotaApi.create(SalesQuotaRequest)` 的请求/响应类型**一字不动**。
- [x] **不产出 `contracts/`**：本项只改界面容器，**没有任何接口形状的变化**。

### 二、分层架构与关注点分离（不可协商）
- [x] 弹窗表单住在**页面层**（`pages/quotas/QuotaListPage.tsx`），与全站 55 处「列表页 + 表单弹窗」同层同构；
      **不新建 `components/**` 组件**（无 per-entity 弹窗组件先例，且会被 R7 孤儿检查看着）。
- [x] 表单外壳**复用既有原语** `components/ui/FormModal.tsx`，**不重写**弹窗契约（Enter 提交 / `destroyOnClose` / `confirmLoading` / 默认 `cancelText` 由它承担）。
- [x] 取数仍走既有 `quotaApi`；**不引入第二套刷新机制**（`refreshToken` 进既有 `useEffect` 依赖数组）。

### 三、数据完整性、安全与校验（不可协商）
- [x] **校验规则逐字不变**：3 条 `required` 与它们的 `message` 键原样搬运；`initialValues` 不变。
- [x] **失败不静默**：`create` 拒绝时给 `message.error`、**弹窗保持打开**（不许关掉让用户以为成功）。
- [x] **不吞异常也不制造孤儿 rejection**：`catch` 里报错后**不 rethrow**（`FormModal.handleOk` 是 `try/finally` 无 `catch`，见 `CustomerListPage.tsx:125-126` 的同条注释）。
- [x] 无密码/密钥/网络/事务涉及。**不碰共享开发库**（只读冒烟；写库冒烟需用户明确同意，见 §验证）。

### 四、测试优先与质量门禁（不可协商）
- [x] **新增 5 组行为层用例**（`QuotaListPage.form.test.tsx`），覆盖：弹窗开合与契约默认值、**校验拦截且不发请求**、payload 组装（含日期格式化）、**两条刷新各自独立**、失败保开。
- [x] **章程说「测试先于实现」（红→绿）**：⚠️ **如实说明边界**——本项实际顺序仍是**先实现、后补用例**（沿用 087/088/092/095–098 的既有做法），
      **不得**据此声称走过 spec-first；定向破坏留痕证明的是**护栏有牙齿**，不是「红先出现」。
- [x] 测试金字塔：**不新增 e2e 用例**；e2e 只作为**既有清单的一致性检查**（且只跑受影响的那一条，见 §验证）。

### 五、简洁、可维护与可观测（不可协商）
- [x] **YAGNI**：不抽组件、不引 state 库、不改 `FormModal` 契约、不给弹窗加二次确认。
- [x] **删掉的东西多于加上的**：净删 1 个文件（126 行）+ 1 条路由 + 1 个 i18n 键。
- [x] **可观测**：失败既有 `message.error`，也有 KPI/表格的可见刷新（用户能直接看出「成了」）。

**Gate 结论**：五项原则**无违规、无需 `Complexity Tracking`**。

---

## 已核实事实（实测/读实现而得，可直接采信）

**基线与判据（098 交付时的实跑读数，本项**不沿用**为交付值，只作对照）**：
`check-i18n` 路由 **58** 条、`check-menu` 菜单 **56** 项、`check-perms` **68** 码 / 8 文件 9 处 ADMIN、
`check-ui` **272** 文件 · **304** `Form.Item` · 冻结 **54** 未增长 · `CANDIDATE_READINGS.R2 = 55`、
`check-zh` **269** 文件 · 候选 **9158** · 命中 **266 处 / 4 条台账** · `App.tsx` count **55**、
覆盖率 **71.36 / 75.26 / 39.24 / 71.36**（阈值 **33.6 / 47.2 / 21.4**）。

**① R2 对 `<FormModal>` 隐形（选型依据，读了实现）**：
`scanTagEvents(code, 'Modal')` 的针是 `<Modal`，且要求后一字符不匹配 `[A-Za-z0-9_.]`
（`frontend/scripts/check-ui.mjs:97-118`——这条守卫是为了 `<Form` 不命中 `<FormItem`）。
`<FormModal` 不含子串 `<Modal` ⇒ R2 **看不见它** ⇒ 用原语**天然过 R2**且 `CANDIDATE_READINGS.R2`（`:782`）**不动**；
裸 `<Modal>`+`<Form>` 则 55→56（只印不判）且**必须**写 `width`。

**② 删这条路由不动任何机器计数（逐条核过判据，不是「跑一遍没红」）**：

| 门禁 | 判据 | 删 `<Route path="quotas/create" .../>`（`App.tsx:879`）后 |
|---|---|---|
| `check-i18n` | 路由枚举正则要**单引号 + 同行 `name:`**（`:176`） | **不变（58）**——该行是**双引号、无 `name:`**，从来就没被数进去 |
| `check-menu` | 从后端 `RoleConstants.MENU_TREE` 重生成后逐字节比对 | **不变（56）**——`/quotas/create` 不在 `MENU_TREE` |
| `check-perms` | 68 码 + `PERMS.` 引用核对 | **不变（68）**——该页无 `PERMS.` 引用，`permissions.ts` 无 `quota:*` |
| `check-zh` | `ZH_ALLOWED` 双向校验，`App.tsx` count **55** | **不变（55）**——那行没有中文字面量 |
| `check-ui` | 文件数/`Form.Item` 数**只印不判**；`MIN_CANDIDATES` 各规则远高于下限 | 只印的数变：**272→271**、**304→303**（⚠️ **实做订正 2026-09-16**：立项期推算写的 `304 → 298` **有误**，提交 2 后实跑为 **303**；理由见 `falsification-evidence.md` §0） |
| `check-zh` | 文件数**只印不判** | 只印的数变：**269→268** |

⚠️ **反例（不许碰）**：`App.tsx` 的 `{ path: '/quotas', name: '销售配额' }` 一删，
`check-i18n` 的 `orphanKeys` 与 `check-zh` 的第三分支**同时转红**。

**③ KPI 不跟刷（本项最容易漏的真缺陷）**：`getSummary(year)` 的 `useEffect` 依赖只有 `[year]`（`QuotaListPage.tsx:19-24`）；
`actionRef` 已声明（`:15`、`:137`）但**从未调用过 `reload()`** ⇒ 「提交后全刷新」**必须显式加一条通路**。

**④ `pages.quotaCreate` 命名空间实测 17 键**（`zh-CN.ts:3166-3183`，`en.ts` 对应块同构）：
删文件后 **`btnBack` 成死键**（弹窗无返回按钮），其余 **16 键**在弹窗里继续用。

**⑤ e2e 会有一条**变成空真**的条目**：`module-page-auth.spec.ts` 的 `FORM_PAGES` 逐字列着 `'/quotas/create'`（`:251`），
`:143-145` 的注释正以它当「纯表单页」的例子。删路由后 `goto` 会落到 `NotFoundPage` ⇒ **多半仍绿但断言空真**，必须摘掉（不许留着当绿）。

**⑥ 既有覆盖缺口（决定本项的证据形态）**：`QuotaCreatePage` **零单测**；`frontend/e2e/` **无任何用例走创建流程**
（配额只被导航型用例访问：`module-page-auth` 只 `goto` + 断言无 401 / 非白屏；`geometry-list-page` 只测 `/quotas` 几何；`role-permissions` 只 `goto('/quotas')`）。
⇒ 行为层证据**只能来自新单测**。

---

## 结构决策

**产出 `research.md`，不产出 `data-model.md` 与 `contracts/`**：本项**无实体、无字段、无端点、无迁移**。
为凑齐工件而生成空壳文件正是「为了流程而流程」（与 092/095–098 的先例一致）。

**表单内联进 `QuotaListPage.tsx`**，不抽 `components/**/QuotaCreateModal.tsx`：
本仓**没有** per-entity 的 `XxxFormModal.tsx`（全库唯一的实体级抽取弹窗 `LeadConvertModal.tsx` 是因为被**两处**复用），
且 `src/components/**` 被 R7（孤儿组件）看着。

**外壳用共享原语 `FormModal`**（`size="lg"` = 800px），**不手写裸 `<Modal>`**：见「已核实事实 ①」。

**保留纵向布局、删掉 `maxCols`**：`FormGrid` 的文件头（`:63-65`）明说 `maxCols` 是给**页面级宽容器**用的；
弹窗是 480–960 的窄容器，`auto-fit` 按容器宽度自然排 2 列。`minItemWidth={VERTICAL_MIN_ITEM_WIDTH}` **保留**（纵向布局的既有下限）。

**删旧页面与加弹窗必须同一次提交**：分两次会让中间提交留下「指向已删文件的路由」（`typecheck`/`build` 红）。

**消息 API 用 `App.useApp()`**：本仓 69 个文件走这条（静态 `message` 只有 10 个）⇒ 随主流。
⚠️ 测试侧的 `renderWithProviders`（`src/test/renderWithProviders.tsx:20-25`）已包 `<AntApp>` ⇒ 单测里可用。

**工件产出方式**：**手写**（照 `.specify/templates/` 与 086–098 已入库工件的体例），
**不运行任何 `/speckit-*` 命令**，**不碰 `.specify/feature.json`**（共享单槽指针、gitignored、从历史里恢复不了）。

### 一个数字住在好几个地方（落点清单）

| 数字 | 落点 | 本项取值 |
|---|---|---|
| i18n 键数 | `zh-CN.ts`/`en.ts` 实测、`PROJECT_FEATURES.md` 的 i18n 行、`specs/README.md` 的 099 行 | **2962**（**实跑 `i18n:check` 为准，不推算**） |
| Spec 模块数 | `PROJECT_FEATURES.md`、`specs/README.md` 编号说明、`README.md:163`、`roadmap.md` 覆盖度行 | **98（001–099，缺 069）** |
| 测试文件/用例数、覆盖率 | `tasks.md` 交付块、`research.md`、`specs/README.md` 099 行 | **交付时实跑取值** |

⚠️ 订正一律**原文逐字保留 + 带日期 ⚠️ 块**，粒度到**每一列**；自查判据是「**旧值仍能被 grep 到**」。

---

## Project Structure

### Documentation (this feature)

```text
specs/099-quota-create-modal/
├── spec.md                    # 用户故事与验收（SC-099-001–008）
├── plan.md                    # 本文件
├── research.md                # 与 078 的关系、R2 隐形机制、历史基线失去对象、三条既有空档
├── quickstart.md              # 门禁命令 + 只读冒烟步骤 + 定向破坏清单
├── falsification-evidence.md  # 定向破坏的逐字留痕（含还原判据）
├── tasks.md                   # 任务分解
└── checklists/requirements.md # 规格质量自查
```

**不产出** `data-model.md` / `contracts/`（理由见「结构决策」）。

### Source Code

```text
frontend/src/pages/quotas/QuotaListPage.tsx          # 改：加弹窗（FormModal lg）+ refreshToken + reload + 按钮改开弹窗
frontend/src/pages/quotas/QuotaCreatePage.tsx        # 【删】整文件（126 行）
frontend/src/pages/quotas/QuotaListPage.form.test.tsx # 【新】5 组行为层用例
frontend/src/App.tsx                                 # 改：删该页 lazy import 与 <Route path="quotas/create">
frontend/src/i18n/zh-CN.ts · frontend/src/i18n/en.ts  # 改：同批删 pages.quotaCreate.btnBack
frontend/e2e/module-page-auth.spec.ts                # 改：FORM_PAGES 摘条 + 注释带日期 ⚠️ 订正

PROJECT_FEATURES.md · specs/README.md · specs/roadmap.md · README.md
├── specs/083-engineering-consolidation/quickstart.md                 # 订正：必访路由清单
```

⚠️ **`PROJECT_FEATURES.md` 的配额段**（记述「新增 `QuotaCreatePage` 与 `/quotas/create` 路由」）与
**`083/quickstart.md` 的 14 条必访路由清单**：原文保留 + 带日期 ⚠️。
⚠️ **`specs/088/tasks.md`、`specs/090/{spec,plan,tasks}.md`**：以该页为几何/宽度取样点的**历史实测**，**一字不动**。

---

## 分步与提交（4 次提交，各自可回退，**每次提交后门禁都必须是绿的**）

| # | 提交 | 内容 |
|---|---|---|
| 1 | `docs(099): 立项` | 本目录 7 件工件 + `specs/README.md` 模块表 099 行（状态写「⏳ 进行中」）+ 编号说明纳入 099 + `roadmap.md` 的 099 行（**勾选框留空、不预勾**）与两条聚合数订正 + `README.md:163` |
| 2 | `refactor(099): 配额创建改为列表页内 FormModal 弹窗` | `QuotaListPage.tsx` + **删 `QuotaCreatePage.tsx`** + `App.tsx` 删 import/Route + i18n 两侧删 `btnBack` + e2e `FORM_PAGES` 摘条与注释订正（**必须同批**） |
| 3 | `test(099): 配额创建弹窗的行为层用例` | `QuotaListPage.form.test.tsx`（5 组断言） |
| 4 | `docs(099): 实测读数、勾选与数字收口` | `falsification-evidence.md` 实测输出 + `tasks.md` 勾选与交付块 + `PROJECT_FEATURES.md`（i18n 现值 / 模块数 / 配额段订正）+ `083/quickstart.md` 订正 + `specs/README.md`/`roadmap.md` 交付态 |

---

## 验证

### 单测（本项唯一的行为层回归证据）

`frontend/src/pages/quotas/QuotaListPage.form.test.tsx`，5 组（照 `pages/tags/TagListPage.form.test.tsx` 与 `pages/departments/DepartmentListPage.test.tsx` 的既有读法）：

1. **弹窗契约**：点「新建」⇒ 弹窗开、标题键 `pages.quotaCreate.title` 在场、
   `cancelText` 吃到**默认值** `common.button.cancel`、宽度 = **`lg` 档 800**（读 `.ant-modal` 的内联 `style`，照 `CustomerListPage.form.test.tsx:132`，**不用** `getComputedStyle`）。
2. **校验拦截**：必填留空点保存 ⇒ **字段级错误出现** **且** `quotaApi.create` **未被调用**（只断「没调用」是假绿）。
3. **payload**：填全提交 ⇒ `create` 收到的 `SalesQuotaRequest` 含 `year` 默认值、`amount`，且 `periodStart`/`periodEnd` 是 **`YYYY-MM-DD`** 字符串。
4. **两条刷新分开断言**：成功后弹窗关闭 + **表格重拉**（`list` 再被调一次）+ **KPI 重取**（`getSummary` 调用次数 +1）。
5. **失败保开**：`create` 拒绝 ⇒ 弹窗**仍开着**（不许静默关掉）。

⚠️ 三条既有坑：`src/test/setup.ts` 把 `react-i18next` mock 成 `t(key) → key` ⇒ **断言键名，不断言中文**；
`destroyOnClose` 让字段在开弹窗时才挂载 ⇒ 取值/预填类断言用 `waitFor`；
vitest 在本机会超订 ⇒ 用文件内 `it(name, fn, 60_000)` 放宽耐心，**不动断言**。

### 门禁（每次提交前）

```bash
cd frontend && pnpm typecheck && pnpm lint && pnpm i18n:check && pnpm menu:check \
  && pnpm perms:check && pnpm ui:check && pnpm zh:check && pnpm test:coverage && pnpm build
```

- 逐条 exit 0；`i18n:check` 两侧键数**相等**且 = **2962**（**实跑取值**，三个落点一致）。
- `ui:check`：冻结台账**不得增长**、**`R2` 候选仍为 55**（用原语 ⇒ 候选池不变）、扫描文件数应为 **271**（实跑为准）。
- `zh:check`：命中与台账**均不变**（`App.tsx` 的 55 不动）、文件数应为 **268**。
- `menu:check`（56）/ `perms:check`（68 码）应与 098 交付时**逐字相同**（本项不动菜单与权限码）。
- `pnpm build` 是**本项新加的一道**：它只证明**编译得过**，**不证明**「按钮指向的路由存在」（见破坏 D5）。
- **覆盖率**：四项对 **33.6 / 47.2 / 21.4** 均高于且**阈值未下调**，**不与上次的小数位比**；
  **工区须无第二个写入者**（判据：`git status --porcelain` 只有本项工件）；
  Branch 若差 0.01 照 098 先例**单列一节写明原因未查明**。
- **后端不跑**（无 Java 改动）。

### 定向破坏留痕（每条都要**被观测到转红**；逐条做、逐条还原；破坏期间不提交）

**还原判据分两类**：本应等于 HEAD 的文件用 `git hash-object <file>` == `git rev-parse HEAD:<path>`（内容级相等，**不称逐字节一致**）；
**有意未提交**的本项工件改用「还原后复跑读数与破坏前**逐字相同**」。
**破坏-还原一律 `cp` 备份回写，禁用 `git checkout`**（它会吞掉同文件里本项有意未提交的订正）。

| # | 令其转红的方式 | 该红的判据 |
|---|---|---|
| D1 | 删 `size="lg"`（退回默认 md） | 单测 1 的宽度断言转红 |
| D2 | 删 `onSubmit` 里的 `validateFields()` | 单测 2 转红（**校验不再拦截**） |
| D3 | 删 `actionRef.current?.reload()` | 单测 4 的**表格重拉**那半转红 |
| D4 | 删 `setRefreshToken(...)` | 单测 4 的**KPI 重取**那半转红（**D3/D4 必须分开做**） |
| D5 | 把 `navigate('/quotas/create')` 写回按钮 | **预期仍绿** ⇒ 这就是「**没有门禁看着 `navigate` 目标是否存在**」的实测。**如实记为已知空档**，替代判据 = grep 零命中 + 只读冒烟 |
| D6 | 只从 `en.ts` 删 `btnBack`（`zh-CN.ts` 不删） | `check-i18n` 的双向键集合比对转红（**双语必须同批**） |
| D7 | 把 `FormModal` 换成裸 `<Modal>` 且**不写** `width` | `check-ui` 的 **R2** 转红（证明「用原语」是这条规则的合规路径，不是风格偏好） |

### 手工冒烟（边界如实写）

**只读冒烟（默认做）**：8081 后端与 5173 前端本会话已在跑 ⇒ 打开 `/quotas` → 点「新建」→ 弹窗开、6 个字段与文案在、
必填校验在、Esc/取消能关、**不点提交**（不写库）。这条**不需要额外同意**（不写任何数据）。

**写库冒烟（不做，除非用户明确同意）**：提交会往**共享开发库**写入一条配额 ⇒ 按仓规**未经明确同意不得对共享库做任何操作**。
若用户要端到端验证，照既有配方起**隔离实例**（临时端口 + 独立 schema + 另一个 Redis db），收尾 `DROP`/`REVOKE`/`FLUSHDB` 并核对共享库未动。

### e2e（只跑受影响的一条，不跑整套）

`frontend/e2e/module-page-auth.spec.ts` 是**唯一**被本项直接影响的 e2e 文件（**导航型**：不填表、不提交）。
**前置**：8081 上要有后端。**不擅自跑整套**（其余 spec 可能写共享库）。
若因故没跑，**如实写明「未跑 e2e」**，**不得**用门禁全绿冒充端到端证据。

---

## 风险

| 风险 | 缓解 |
|---|---|
| **KPI 不刷新**（本项最可能的真缺陷，且**门禁抓不到**） | 用户已裁决「全刷新」；**D4 单独破坏**证明它有用例看着 |
| `onSubmit` 里 rethrow ⇒ 无人接管的 promise rejection | 明文约定：`catch` 里 `message.error` 后**不 rethrow**（`CustomerListPage.tsx:125-126` 的同条注释） |
| 删文件删出「指向不存在文件的路由」的中间态 | 删文件 + 删路由 + 加弹窗 **同一次提交**；`pnpm build` 兜底 |
| 误以为「按钮指向不存在的路由」有门禁 | **D5 实测证伪** ⇒ **如实披露**这条空档，替代判据是 grep 零命中 + 只读冒烟 |
| 只断「`create` 没被调用」造成**假绿** | 单测 2 同时断**字段级错误出现**（照 `TagListPage.form.test.tsx:124-125` 的既有教训） |
| 数字落点漏改（键数/模块数各住 3–4 处） | 提交 4 逐落点核对；以「旧值仍能被 grep 到」作订正留痕自查 |
| 把 e2e 的空真当绿 | 摘掉 `FORM_PAGES` 那条 + 注释带日期订正，**不许留着当绿** |

## 明确不做

见 `spec.md` §4（8 条，每条附理由）：不加前端权限码、不动字段语义/校验/文案、不弹窗化其余 4 个子页、不改 078 工件、
不改 088/090 的历史读数、不抽 `components/**` 组件、不碰后端与 `.specify/feature.json`、不清 `App.tsx` 的 2 处真欠账。

## 提交纪律

`ListAgents` 确认无并行会话写同一批文件 → **逐路径 `git add`，禁用 `git add -A` / `git commit -a`**（本仓多会话共用工作区）→
提交信息末尾带 `Co-Authored-By: Claude Code <noreply@anthropic.com>`。若同伴工作被卷入，用 `git reset --soft` 重做，
**绝不修改或丢弃另一会话的未提交工作**。**不把提交自己的哈希写进它携带的文件**；**复选框绝不回填/预勾**（`tasks.md` 只在交付时勾）。
