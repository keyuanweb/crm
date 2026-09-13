# 功能规格：接线码的渲染层用例补课

**模块**: 087-perm-test-backfill
**形制**: **加固类**（与 003 / 083 / 085 同形制——不产 `contracts/`、无 Flyway 迁移、**零生产代码改动**）
**归属**: 一期 1.5 的**追补**，不属 086
**优先级**: P1

---

## 背景：本规格的由来

086（前端按钮级权限收口）在收尾时做了一次机械盘点（记于 `086/tasks.md` 的 T103），把接线分成两半：

| 类别 | 码数 | 渲染层用例 |
|---|---|---|
| 086 自己收口的码 | 46 | **全覆盖**（39 个 `*.perm.test.tsx` / 146 条用例，逐条变异自验） |
| 1.5 批 1~3 早已接线的码 | 48 | 只有 33 个被顺带覆盖 |
| ↑ 其中**无任何渲染层用例**的 | **15** | **0** |
| 另有 3 个码被上表的"字面量出现"口径**误判为已覆盖**（见下文「范围订正」） | **+3** | **0** |

这 15 个码是**本规格的主体范围**。它们不是"漏接线"——线早就接好了——而是**没有任何测试能证明接线是对的**。

### 范围订正：15 → 18（执行期发现，2026-09-13）

执行到收尾时，我用**正确的口径**（"该码的判据落点所在的页面里，是否有一条用例引用它"）重扫了全仓，而不是沿用 086 的"码字面量是否出现在任何测试里"。结果是：**全仓 68 个「页面 × 码」判据点里，有 4 处没有本页用例，而它们全都不在上面的 15 个码里**。

原因正是上面那段口径问题——这 4 处的码字面量**坐在别页的负向夹具里**，于是被 086 的口径判为"已覆盖"：

| 判据点 | 码 | 误判来源 |
|---|---|---|
| `marketing/CampaignListPage:200` 行内「删除」 | `campaign:delete` | `landing/LandingPageListPage.perm.test.tsx:104` 的负向夹具 |
| `email/EmailUnsubscribePage:41`「恢复」 | `email:manage` | 同上（同一行的夹具里同时列了 `email:manage` 与 `campaign:delete`） |
| `marketing/EmailCampaignPage:183`「测试发送」 | `email:manage` | 同上 |
| `exports/ScheduledExportExecutionHistoryPage:104`「立即执行」 | `export:scheduled` | 同类 |

所以 `campaign:delete` **不是孤例，而是这一类的一个样本**——086 的 T103 只点出了它一个，没有把这一类扫完。这 4 处与 15 个码**同类同源**（都是"已接线、零本页用例、错了也不会有任何信号"），本规格按同类扩入，**范围由 15 个码订正为 18 个码**（新增 `campaign:delete` / `email:manage` / `export:scheduled`）。

> **为什么必须扩**：本规格存在的全部理由就是"没有证据的接线是风险"。若带着一个**自己的文档已经承认、却没有做**的洞收工，那就是本规格要消灭的那类假门禁的翻版。扩入的成本是 4 个站点 / 8 条用例，机械且同源。

**为什么这不是吹毛求疵**：086 自己给出了反例。`campaign:delete` 的**字面量确实出现在测试文件里**，
看起来"有覆盖"，但逐个核对后发现它只是 `LandingPageListPage.perm.test.tsx:104` 里的一个**负向夹具**
（断言"该角色不该因此拿到能力"），而它真正的判据落点 `CampaignListPage.tsx:200` **从未被任何用例执行过**。
于是"数一数测试里出现过多少个码"这种口径会给出**虚高的覆盖率**——
本规格的 T003 因此把口径写成"有**正向**用例"，而不是"码字面量出现在测试里"。

**先前已记录但未做的原因**（`086/tasks.md` T103 原文）：
"是否为这 15 个既成接线的码补测，是一个需要裁决的范围问题，不是本次的疏漏"。
2026-09-13 由用户裁决：**做，且单独立项**（理由之一是不该把追补塞进已闭合的 086，那会污染它的验收口径）。

---

## 本规格的独特风险：**假绿**

086 的风险是"挂错码 ⇒ 按钮静默消失"，本规格的风险不同——**是"用例写了但什么都没证明"**。具体有两种形态：

1. **空断言**：用例只渲染页面就 `expect(...).toBeInTheDocument()` 一个**恒存在**的元素（如标题、返回按钮），
   判据错了也不红。086 的既有用例都用"永远渲染的锚点"来防这个，但那是**纪律**，机器抓不到。
2. **正向用例写成恒真**：用 ADMIN 用户跑正向用例——ADMIN 在 `hasPerm` 里**短路直通**
   （`hooks/usePermission.ts:10`），于是"有码 ⇒ 可见"这条断言对**任何**码都成立，判据错了也不红。

**对策是纪律 + 变异自验**（见 FR-003）：每一条负向用例都必须被观察到在判据被改成恒真后**转红**，
还原后转绿。这不写进机器门禁（成本不划算），但写进本规格的验收标准（SC-002）。

---

## 用户场景与测试 *（必填）*

### 用户故事 1 - 无权者看不到那些按钮（优先级：P1）

作为**只有部分权限的销售**，当我的角色未被授予某个码时，那个码管着的按钮不该出现，
因为我看见它就说明接线断了或判据写错了。

**验收**：对 15 个码中的每一个，用一个**非 ADMIN**、`permissions` 不含该码的用户渲染目标页面，
断言该码管着的按钮/动作**不在**文档里。

### 用户故事 2 - 有权者确实看得到（优先级：P1）

作为**被授予了该权限的销售**，我该看到那些按钮——否则接线把判据挂反了（或挂到了一个没人持有的码上）。

**验收**：对同一个码，用一个**非 ADMIN**、`permissions` 含该码的用户渲染，
断言同一个按钮**在**文档里。

> **US1 与 US2 必须成对**。只有 US1 会放过"判据挂在恒假的码上"（按钮对所有人消失）；
> 只有 US2 会放过"判据恒真"（按钮对所有人出现）。086 的两条断言正是为此成对设计的。

### 用户故事 3 - 页面上的非显然判据被显式覆盖（优先级：P2）

作为**维护者**，我希望那些"看起来是同一个按钮、其实是不同码/不同视图"的地方被用例钉住，
因为它们最容易在重构时被改错。

**本规格已知的非显然点**（T001 逐页核对时确认，详见 plan.md 的映射表）：

- `CustomerListPage` 的「认领」**只在池视图（`view === 'pool'`）下出现**——
  离开池视图时该按钮不存在，与权限**无关**。用例必须分别覆盖两种视图，否则会把视图差异误读成权限差异。
- `CustomerListPage` 的 `customer:pool_manage` **一个码管三处**（表格行选择、池扫描、批量转移），
  三处必须同进同出。
- `ProductListPage` 的 create / update / delete **三码各不相同**，不能只测一个就推定另两个。

### 边界场景

- 用户 `permissions` 为 `undefined`（而非 `[]`）时——`hasPerm` 走 `(user.permissions ?? [])`，应等价于空。
- 同一个页面由**两个码**分别管两个按钮：授 A 不授 B 时，断言 A 在、B 不在（**互不串门**）。
  `CampaignListPage`（create/update）与 `ProductListPage`（create/update/delete）是重点。
- ADMIN 用户：**必须**看到全部按钮（这是既有语义，`CustomerListPage.test.tsx` 依赖它）；
  但 ADMIN **不得**被用作负向用例的主体。

---

## 需求 *（必填）*

### 功能需求

- **FR-001**：为下列 **18 个码**各写至少 2 条渲染用例（US1 一条、US2 一条），共 **≥36 条**：
  `customer:{create,delete,import,claim,pool_manage}`、`contract:approve`、`quote:approve`、
  `lead:assign`、`order:delete`、`product:{create,update,delete}`、`campaign:{create,update,delete}`、
  `role:manage`、`email:manage`、`export:scheduled`。
  后 3 个码（`campaign:delete` / `email:manage` / `export:scheduled`）是**执行期按正确口径重扫后扩入**的，
  依据见上文「范围订正」。
- **FR-002**：所有负向用例的主体**必须是非 ADMIN** 用户（`role: 'SALES'` 等），
  且 `permissions` 显式不含目标码。**ADMIN 只能用于正向或"全可见"断言**。
- **FR-003**：每条**负向**用例都必须经**变异自验**：把对应判据临时改成恒真，观察该用例**转红**，
  再还原。还原必须用**唯一探针标记行**替换，**禁止** `replace_all` 作用于短常见字面量
  （2026-09-13 有 `replace_all` 把 6 处无关代码一起改掉、且类型检查沉默的前例）。
- **FR-004**：断言必须命中**该码管着的那个控件**，不得用恒存在的元素（标题、返回按钮、
  表格容器）充当"有权限"的证据。允许用恒存在的锚点做**非空性**证明（证明页面确实渲染了）。
- **FR-005**：**零生产代码改动**。本规格只新增 `*.perm.test.tsx`（必要时可新增测试辅助文件）。
- **FR-006**：覆盖面必须**可复核**：`tasks.md` 要给出一张"码 → 用例文件 → 用例名"的对照表，
  并注明每条是正向还是负向。
- **FR-007**：沿用 086 的形制与工具，不引入新的测试库或新的渲染辅助：
  `src/test/renderWithProviders.tsx`（`ConfigProvider(zhCN)` + antd `App` + `QueryClient(retry:false)` + `MemoryRouter`）、
  `src/test/setup.ts` 的 i18n mock（`t(key) => key`，**缺键时抛错**）。

### 关键实体

- **目标页面（11 个）**：`CustomerListPage`、`ProductListPage`、`CampaignListPage`、`ContractDetailPage`、
  `QuoteDetailPage`、`LeadListPage`、`OrderListPage`、`RoleListPage`、
  `EmailUnsubscribePage`、`EmailCampaignPage`、`ScheduledExportExecutionHistoryPage`（后 3 个为范围订正后新增）。
- **用例文件**：与页面同目录、`<PageName>.perm.test.tsx`（086 的既有命名）。
  `ContractDetailPage.perm.test.tsx` **已存在**（086 为 `contract:update` 而写），
  本规格的 `contract:approve` 用例**追加进该文件**而非新建，避免两个文件测同一页面。

---

## 成功标准 *（必填）*

- **SC-001**：18 个码 × 2 向，**≥36 条**新用例，`npx vitest run <本规格涉及的文件>` 全绿。
- **SC-002**：**每一条**负向用例都被观察到在判据恒真化后转红（逐条记录，`tasks.md` 留痕）。
- **SC-003**：前端六道门禁（`typecheck` / `lint` / `i18n:check` / `menu:check` / `perms:check` / `test:coverage`）
  全部退出码 0；后端 `FrontendPermissionCodeAlignmentTest` 保持绿（本规格不动 `permissions.ts`，预期自然保持）。
- **SC-004**：`git diff --stat` 中**不含任何生产代码文件**（只有 `*.perm.test.tsx` 与 `specs/087-*/`）。
- **SC-005**：无残留变异探针——`grep -rn "MUTATION-PROBE" frontend/src/` 为空。
