---
description: "任务清单：仪表盘名实相符（093）"
---

# 任务清单：仪表盘名实相符（093）

**Input**: Design documents from `/specs/093-dashboard-truthfulness/`

**Prerequisites**: [plan.md](./plan.md)（必需）、[spec.md](./spec.md)（必需，用户故事与 FR 来源）

**Tests**: **必需**。SC-005 明文要求每条新断言**被观察到能变红**。

**Organization**: 按阶段（P1 i18n → P2 页面 → P3 测试 → P4 门禁 → P5 文档）。P1 是 P2 的**前置**，理由见 plan.md。

## 格式：`[ID] [P?] 说明`

- **[P]**：可并行（不同文件、无未完成依赖）
- 每项任务均含**确切文件路径**
- **实测证据一律内嵌日期**

> **编号消歧**：本文档的 `T0xx` 属于本规格，与 `specs/083-*`~`specs/092-*` 的同名编号**无关**。
> 引用其他规格时**一律加前缀**。

## 路径约定

- **前端**：`frontend/src/`、`frontend/scripts/`
- **后端**：**只读**——本规格零后端改动、无 Flyway 迁移、无契约变更（与 085/086/087/090/091 同类）

---

## P1：i18n（P2 的前置）

- [x] **T001** 在 `frontend/src/i18n/zh-CN.ts` 删除与**被删代码 1:1 对应**的键：
      `pages.dashboard.todo.*`（`:930-937`，8 键）、`pages.dashboard.activity.*`（`:938-949`，12 键）、
      `home.trendVsLastMonth`（`:103`）、`pages.dashboard.statCards.totalCustomers`（`:846`）、
      `pages.dashboard.statCards.activeOpportunities`（`:847`）。
      **删除后 `statCards`（`:841-848`）恰好剩 4 键**，与 FR-D02 一一对应 ⇒ SC-003。
- [x] **T002** 在 `frontend/src/i18n/en.ts` 删除**同名同组**的键。**必须与 T001 同一提交**：
      只删一侧会被 `check-i18n.mjs:58-72` 的**双向**比对抓住（它的历史由来见 `check-i18n.mjs:7-8` 的 `pages.ticket.list.colStatus`）。
- [x] **T003** 在 `zh-CN.ts` 的 `pages.dashboard.forecast`（`:881-886`）增 4 键：
      `probability`（概率）、`weighted`（加权金额）、`source`（概率来源）、`fixedProbability`（固定概率）、
      以及 `scopeNote`（口径说明，全文「口径：全部在途商机，不含月度目标」）与 `empty`（空态）。
      **`fixedProbability` 不是可有可无的**：`probabilitySourceOf` 有 **3** 个取值
      （`DashboardStatsService.java:218-223` 的 FIXED / HISTORICAL / DEFAULT），
      而既有 i18n 只给了后两个键（`historicalCalibration`、`defaultProbability`）。
- [x] **T004** 在 `zh-CN.ts` 的 `pages.dashboard.followUpActivity`（`:904-908`）增 `byMethod`（按方式分布）。
- [x] **T005** [P] 为 T003/T004 的每个新键在 `en.ts` 补英文值。**与 T003/T004 同一提交**。
- [x] **T006** 跑 `pnpm i18n:check` 确认两语言键集合双向相同。

## P2：页面

- [x] **T007** `frontend/src/pages/stats/DashboardPage.tsx`：`KpiCard`（`:58-117`）删 `trend` prop 与
      `:93-97` 的渲染块。**四个 `trend={{...}}` 传参（`:654/664/674/684`）必须同时删**（FR-004）
      ——留着 `trend` prop 而只删传参，会留下一个永远不会被调用的死 prop。
- [x] **T008** KPI 行（`:647-686`）按 FR-001/002/003 重排为 **商机总数 / 金额合计 / 赢单率 / 本月新增客户**：
      卡 1 标签 `statCards.activeOpportunities` → **`statCards.opportunityCount`**（值不变，本就是全部商机数）；
      卡 3 新建为 `statCards.winRate`，值 `(summary.winRate * 100).toFixed(1) + '%'`。
      **注意 `winRate` 是 0..1 小数**（`DashboardStatsService.java:136`），**不是**百分数——直接渲染会得到 `0.4%`。
- [x] **T009** 删 `TodoList`（`:389-421`）与 `ActivityFeed`（`:424-453`）两个组件定义（FR-012）。
- [x] **T010** 删 `todos`（`:529-534`）与 `activities`（`:536-542`）两个 `useMemo`；清理因此变为未使用的导入
      （`Timeline` 仅 `ActivityFeed` 用；`useMemo` 需确认仍被 `aiStats` 使用）。
- [x] **T011** 新增 `ForecastCard`：主数字 `formatAmount(forecast.weightedAmount)` + 标签 `forecast.weightedTotal`
      + **口径标注 `forecast.scopeNote`**（FR-006 —— 本卡最重要的一行）+ `breakdown` 表
      （阶段走 `stageLabel`、金额走 `formatAmount`、概率按百分比、来源徽标按 `probabilitySource` 三值分支，FR-007）
      + `breakdown` 空时 `forecast.empty`（FR-015）。
- [x] **T012** 新增 `CustomerAnalysisCard`：三项取 `summary.customerCount` / `activeCustomerCount` /
      `newCustomersThisMonth`（FR-008），**不新增任何请求**。
- [x] **T013** 新增 `FollowUpActivityCard`：`followUps.total` + `byMethod[]`（FR-009）；
      方式码映射按 plan.md 决策 5（小写化 + **未知码回退原值**，FR-010）；空态 `followUpActivity.empty`。
- [x] **T014** 新增 `RecentFollowUpTable`：`followUps.recent[]`，六列严格取 `recentFollowUp.*`；
      `customerName` / `followUpBy` 可空 ⇒ `-`（照 `stalledColumns` 的既有写法）；时间按本页既有格式
      `createdAt.replace('T', ' ').slice(0, 19)`；空态 `recentFollowUp.empty`（FR-011/015）。
- [x] **T015** 版式接线：第 2 行两列（`:798-850`）换成 `ForecastCard` / `CustomerAnalysisCard`；
      在 `:851` 的 `</Row>` 之后、第 4 行之前**插入新的一行**承载 `FollowUpActivityCard` / `RecentFollowUpTable`。
      四个新 `<Col>` 一律 `xs={24} lg={12}`（满足 R3）。
- [x] **T016** 取数接线：`const fc = data?.forecast`、`const fu = data?.followUps`，与既有
      `const s = data?.summary`（`:484`）同段，沿用「安全取值：接口缺字段时避免整页崩溃」的既有意图。
- [x] **T017** 跑 `pnpm typecheck && pnpm lint`。

## P3：测试

- [x] **T018** `frontend/src/pages/stats/DashboardPage.test.tsx:3-9`：**删掉 file-local
      `vi.mock('react-i18next')`**（FR-016），使其继承 `src/test/setup.ts:91-123` 的**校验版** mock。
      **必须双向确认**：删前该文件 5 例绿 → 删后仍逐例绿（两版 mock 对**存在**的键返回同一个值 `key`）
      → 故意删一个 zh-CN 键 ⇒ 该文件**转红**（证明校验版真的接手了）。
      ⚠️ 本页只用 `useTranslation` 的 `t`（`:456`），不用 `Trans` / `i18n.language`，
      故两版 mock 的 API 差异不触及它。
- [x] **T019** 改 `:97` 的断言：`statCards.totalCustomers` → `statCards.winRate`（与 T008 同步）。
- [x] **T020** 新增断言 1（预测卡，US2/FR-005）：fixture `forecast: { weightedAmount: 815000, breakdown: [{ stage, amount, probability, weighted, probabilitySource: 'HISTORICAL' }] }` ⇒
      断言 `forecast.title` 在、`formatAmount(815000)` = **`8,150`** 在、`forecast.historicalCalibration` 在。
- [x] **T021** 新增断言 2（客户分析，FR-008）：`20 / 18 / 5` ⇒ 三值分别可见 + `customerAnalysis.title` 在。
- [x] **T022** 新增断言 3（跟进报表，FR-009/010/011）：`byMethod: [{PHONE,2},{EMAIL,1}]` + `recent` 一行 ⇒
      断言 `followUpActivity.totalFollowUps`、`method.phone`、`method.email`、该行内容文本。
- [x] **T023** 新增断言 4（赢单率，FR-003）：`winRate: 0.4` ⇒ 断言 `statCards.winRate` 与 **`40.0%`** 可见，
      且 `statCards.totalCustomers` **不可见**（`queryByText` 为 null）—— 钉住 FR-D02 的替换。
- [x] **T024** 新增断言 5（**负向，本批次的真凭据**，SC-001）：
      `queryByText('home.trendVsLastMonth')` 为 null（假同比归零的直接证据 —— `t` 返回 key，
      这串 key 只可能来自被删的 `trend` prop）、`queryByText('pages.dashboard.todo.title')` 与
      `queryByText('pages.dashboard.activity.title')` 均为 null。
- [x] **T025** 新增断言 6（**空态**，FR-015）：`byMethod: []` + `recent: []` + `breakdown: []` ⇒
      三个 empty 键可见。**防「只测有数据的路径」的假绿**。
- [x] **T026** **自证会红**（SC-005）：把 T024 的任一条负向断言临时改成 `getByText`，确认该用例**变红**，
      再逐字节还原（`git diff` 对该文件应回到改动前）。留痕写进 spec.md 或后续的交付说明。
- [x] **T027** 跑 `pnpm test src/pages/stats/DashboardPage.test.tsx`。

## P4：门禁

- [x] **T028** 跑 `pnpm ui:check`，**读出** `DashboardPage.tsx` 的 R1 品牌色字面量**实测命中数**，
      据此订正 `frontend/scripts/check-ui.mjs:296-302` 的 `count`（现为 7）与 `reason`。
      **不预测该数**：删除 `ActivityFeed` 至少去掉 `:438` 一处，但重排后的图标底色字面量也会变，
      预测必错。该条目是**双向**校验的 ⇒ 陈旧值会让 `ui:check` 立刻转红（不会静默）。
- [x] **T029** 跑 `pnpm ui:check` 复验，并确认 R3（`<Col>` 断点）在四个新卡位上通过。
- [x] **T030** 跑 `pnpm perms:check`：确认本文件的 `role === 'ADMIN'` **仍是恰好 1 次**
      （`check-perms.mjs:89-95` 的 `count: 1` 未变）。**若红了，回看是否新增了第二个角色判断** ——
      那是本项明确禁止的（spec.md 机器门禁表第 1 行）。
- [x] **T031** 跑七道门禁 + 覆盖率：
      `pnpm typecheck && pnpm lint && pnpm i18n:check && pnpm menu:check && pnpm perms:check && pnpm ui:check && pnpm test:coverage`。
      ⚠️ **跑之前先 `ListAgents` 确认无其它会话在写 `src/pages/**`**（见 T035）。
- [x] **T032** 跑 `cd backend && mvn -B verify`（带 `JAVA_HOME` 指向 JDK 21 的显式导出）。
      **本项零后端改动，故结论应与本批次之前逐字相同**——这是「没有误伤后端」的反证，不是新证据。

## P5：文档

- [x] **T033** `specs/006-sales-dashboard/tasks.md` 末尾**追加**带日期的订正段：
      US2 情形 1（`006/spec.md:39`）/ FR-D07（`:95`）/ FR-D08（`:96`）直到 093 才落地；
      31/31 的勾选**提前于实现**；FR-D06（`:94`）与 `contracts/stats.md` 的「概率固定配置 20%/50%/100%/0%」
      已被 `specs/019-*` 的历史校准取代。
      **不回写历史勾选行**，**不对历史动机下判断**（无法从勾选行分辨「做了但走得早」与「回填」）。
- [x] **T034** `specs/006-sales-dashboard/contracts/stats.md` 的 forecast 段订正：
      固定概率 → 019 的历史校准（`StageConversionService.probabilityFor`，样本不足回退默认）；
      补上契约里缺失的 `probabilitySource` 字段（HISTORICAL / DEFAULT / FIXED）。
- [x] **T035** `README.md:43` 与 `PROJECT_FEATURES.md:36`：仪表盘描述改为
      「KPI 指标、销售漏斗、成交预测、客户分析、跟进活动」。
      现状「KPI 指标、销售漏斗、客户分析、业绩趋势」有两处失实：**业绩趋势**全仓不存在、
      **客户分析**在 093 之前只有 i18n 键无组件（写它是一处「从键的存在推断已实现」的错）。两文件同改。
- [x] **T036** `PROJECT_FEATURES.md` §九「已修复」增一条：仪表盘的两块伪造卡片与 4 个硬编码同比已删，
      006 的三项验收要件已补渲染（带日期）。
- [x] **T037** `specs/README.md`：模块表增 093 行（照 088-092 的列结构：
      `| 093 | 仪表盘名实相符（…） | P1 | ✅ | [目录](./093-dashboard-truthfulness/) | —（纯前端渲染层改动；无新端点、无后端改动、无迁移、无契约变更） |`）；
      `:3` 的形制提要补一句 093。⚠️ **`:3` 目前缺 092 的条目**（092 在模块表 `:122` 有行、提要里没有）——
      这是**既有缺口、不是本项造成**，如实报出、不代填。
- [x] **T038** `specs/roadmap.md`：`## 当前进度` 在 `:253`（092 行）之后增 093 行；
      `:4` 的「最后更新」补 093 交付提要。⚠️ `:160` 的 `006-sales-dashboard` 行**已**把
      「客户分析/跟进报表」写进交付描述（`specs/tasks.md` 的 31/31 之外的第二处提前登记），
      T033/T034 的订正需一并说明这一点。

---

## 依赖与并行

```
T001 → T002 → T003 → T004 → T005 → T006 → T007…T017 → T018…T027 → T028…T032 → T033…T038
                ↑ T005 与 T003/T004 同提交；T002 与 T001 同提交
```

- **P1 必须在 P2 之前**：删键后若页面仍在调用它，`setup.ts:107-113` 的 mock 会在**运行期**抛错，
  而编译期无感——顺序反了会在 P2 期间反复见到假红。
- **T028 必须在 T007–T017 之后**：白名单 `count` 依赖实测。
- **T026 必须在 T020–T025 之后、T031 之前**：它要有断言可改。

## 开工前置（阻塞）

- [ ] **T000** `ListAgents` 确认并行会话 `retire-adversarial-css`（088 会话）**已收工**。
      本文件最近一次改动来自 088 的 `f38b17d`，且 088 未勾选的 **T041**（其余 `layout="vertical"` 表单，
      明文「按文件聚合执行」）很可能把 `DashboardPage.tsx` 算在内——`:901` 正是这样一处。
      本项改动与 T041 的落点**正交**（不动 `layout` / `width`），但**同一文件的并发写入会被 auto-stash 收走**。
      **未确认前不动本文件。**

---

## 执行记录与订正（2026-09-15）

### T000 未满足——如实记，不勾

`ListAgents` 开工时与收尾时各查一次，`retire-adversarial-css`（088 会话）**两次都报 `busy`**，从未「收工」。当时我据以开工的判据是：088 的 `tasks.md` 未勾选数已归零、工作已全部提交（`git show --stat ff4996a | grep -c DashboardPage` = 0 ⇒ 088 的 T041 未触及本文件）。**事后看这个判据不完整**：收尾时工作区里出现了该会话的**新一批在飞工作**——`specs/094-088-debt-closeout/`（未跟踪）、`check-ui.mjs` 里它新加的 `rule8()`（`Descriptions.column` 不得写死为 >1 的字面量）、以及 `SignSection.tsx` / `SurveyBlock.tsx` / `CustomerPortalPage.tsx` 的修改。故 T000 **保持未勾**。

**✅ 续记（2026-09-15，登记清扫时补）**：该前置条件**在事后成立**——`retire-adversarial-css`（088 会话）的
`094` 已提交（`e184fff` / `2bfd18e` / `b9dcc75`），工作区无它留下的未提交改动，且 `ListAgents` 复核时它已不在 busy 侧。
⚠️ **但 T000 的方块仍然不勾**：它问的是「**开工前**是否已确认收工」，而那个时点的事实是「**没有**」——
补勾等于把历史改写成真话，与上文「如实记，不勾」自相矛盾，也与本仓「不回写历史勾选行」的纪律相悖。
**该条件的事后成立与本项的结论无关**：本项是靠「落点正交 + 已提交取证」这一**较弱**的判据开工的，
这一点已如实记在上文，不因条件后来成立而变成「当初就满足了」。

### T031 的读数被并行写入污染——读数是绿的，但「413」不是本项的证据

`pnpm test:coverage` 退出码 **0**，阈值全过：

| | statements | branches | functions | lines |
|---|---|---|---|---|
| 实测 | **69.56** | **73.98** | **37.4** | **69.56** |
| 阈值 | 33.6 | 47.2 | 21.4 | 33.6 |

⚠️ **但测试计数不可归因于本项**：报出 **84 文件 / 413 用例**，而 088 收口时的 `test:coverage` 基线是 **83 文件 / 398 用例**。差额来源之一是 094 留在工作区的探针文件 `frontend/src/tmp-probe-colspan.test.tsx`（132 行 / 3 例，**未跟踪**）——它落在 vitest 的发现范围内，会被一并跑掉。本项自身只新增 **7 例**（`DashboardPage.test.tsx` 5 → 12）。

**这不改变阈值判据的结论**（69.56 对 33.6 的余量不是几个用例能撼动的），但**这个「413」不是本项的读数**，不得作为本项证据引用。按 `vite.config.ts:61-64` 自记的口径，单次运行的小数位本就不作论据。

**可干净归因的是那几道静态门禁**（只扫本项改动的文件，与 094 无关）：`i18n:check` 2876 = 2876；`menu:check` 56 项；`perms:check` 63 码且本文件的 `role === 'ADMIN'` **仍恰好 1 次**；`ui:check` 退出码 0、R1 白名单由**实测** 7 → 8（R1 只扫 `DashboardPage.tsx`，本项独占该文件 ⇒ 该数干净）。四条一起，覆盖 spec.md 机器门禁表的全部四行。

#### 订正（2026-09-15，094 收工后复跑）

上面那段结论**保留原文**，此处追加订正与干净读数。

094 会话（`retire-adversarial-css`）提交完毕、探针文件 `frontend/src/tmp-probe-colspan.test.tsx` 已删、工作区**只剩本项的改动**之后，在**同一工区**复跑 `pnpm test:coverage`：

| | 文件 | 用例 | statements | branches | functions | lines |
|---|---|---|---|---|---|---|
| 交付当日（含 094 探针） | 84 | 413 | 69.56 | 73.98 | 37.4 | 69.56 |
| **订正后（工区只剩本项）** | **83** | **405** | **69.31** | **73.86** | **37.44** | **69.31** |

**405 = 398（088 基线）+ 7（本项新增，`DashboardPage.test.tsx` 5 → 12）**，逐条对得上；文件数回到 83，正是探针被清理的结果。跨会话核对过了同一组数（094 报 **83 / 405**，与本次实测一致）。

⚠️ **两条读数都是绿的、阈值都全过，数值差属正常波动**（`vite.config.ts:61-64` 自记的复跑非确定性）。**须以订正后的 83/405 与 69.31/73.86/37.44/69.31 作为本项引用值**——它不是「更准」，而是**唯一归因干净的那一次**：交付当日那次跑在一个有第二个写入者的工区上，无论其数字是多少，都不能充当本项的证据。原段说的「413 不是本项的读数」**结论成立、依据也成立**，此处只是把「不是本项的」换成「本项的是多少」。

### T032 后端反证：退出码 0，计数差额可归因

`mvn -B verify`（`JAVA_HOME` 显式指向 JDK 21）：**EXIT=0**、`BUILD SUCCESS`、surefire **559 / 0 / 0**、failsafe **286 / 0 / 0**、`All coverage checks have been met.`（阈值 0.73 的**成功结论行**）。

⚠️ 计数与 089 交付时记的 **555 / 284** 不同，差额 **+4 surefire / +2 failsafe**。来源已定位：本批次之前由**本会话**提交的 `a634ddd`（`fix(063)` 手工导出的安全主体修复）本身带后端测试，而 089 的 555/284 记于它之前。⇒ **差额全部归属 `a634ddd`，与本项无关；本项新增后端测试 0 条**——这正是「零后端改动」该有的样子。旁证：`backend/pom.xml` 里**没有任何前端插件**（`frontend-maven-plugin` / `pnpm` / `npm` 均零命中）⇒ `mvn verify` 不碰前端，094 的在飞前端改动影响不到这条读数。

### T019 的落点与原文不同（订正）

原文要求把 `:97` 的断言改成 `statCards.winRate`；实际改为 **`statCards.opportunityCount`**。该用例的语境是「KPI 卡与漏斗」，而卡 1 的标签正是 `opportunityCount`（T008 把标签从 `activeOpportunities` 改成了它）；`winRate` 由 T023 的新用例**单独**断言。照原文改会让卡 1 与卡 3 的断言错位。

### T035 的目标串与原文不同（订正）

原文给的目标串「KPI 指标、销售漏斗、**成交预测**、客户分析、跟进活动」**写于实现之前**。按**实测卡片集**落地为「KPI 指标、销售漏斗、**业绩达成**、**销售预测**、客户分析、跟进活动」：

- `pages.dashboard.performance.title` = **「业绩达成」**——真实存在的一张卡，原文漏了它；
- `pages.dashboard.forecast.title` = **「销售预测」**——与卡面标题逐字一致，优于原文的「成交预测」；
- 原文判「业绩趋势全仓不存在」**正确**且已复核：两份文件的原文都写着它，而卡片集里没有。

一个以名实相符为名的批次，若照抄一份写于实现之前、且本身漏项的清单，就是在消除旧的名实不符时写进新的。

### T037 的 ⚠️ 如实报出：`specs/README.md:3` 另缺 088

`:3` 的形制提要**同时缺 092 与 088 两条**（两者在模块表 `:118` / `:122` 都有行）。092 是 T037 已记录的既有缺口；**088 是本次复核新发现的一条**。按「如实报出、不代填」，两条都**未写入** `:3`。
