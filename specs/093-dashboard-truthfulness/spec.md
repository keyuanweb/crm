# 功能规格：仪表盘名实相符

**模块**: 093-dashboard-truthfulness
**形制**: **改造类**（与 086/088 同类——**改生产代码**，与 003/083/085/087 的「加固类」相对）
**归属**: **新独立批次**，不在一期/二期/三期之内，不动 086/087/088 的任何产物
**优先级**: P1
**取证**: 全部数字由**手工实测**取得，出处逐条内嵌于下（无独立取证脚本——本项要量的东西是"这段代码渲不渲染"，不涉及几何/覆盖率之类需要脚本复跑的量）

---

## 背景：三类「名实不符」，全部实测坐实

`/stats` 是全系统唯一被冻结规格（`specs/006-sales-dashboard`）完整覆盖、却与规格**最不符**的页面。

### 一、两块凭空捏造的卡片

| 卡片 | 坐标 | 伪造形态 |
|---|---|---|
| 「待办事项」 | `frontend/src/pages/stats/DashboardPage.tsx:529-534` | 注释自认「模拟待办数据（实际应从 API 获取）」。三条标题是 i18n `todo.mockApproval/mockFollowup/mockTask` 的写死文案，三个截止日 `2026-08-30` / `2026-08-31` / `2026-09-01` **已全部过期** |
| 「最近活动」 | `DashboardPage.tsx:536-542` | 四条活动的人名是 `张三/李四/王五/赵六`，时间戳用 `dayjs().subtract(10, 'minute')` **现算** ⇒ **它永远看起来是刚发生的** |

第二块的危害高于第一块：过期日期至少会随时间暴露，而相对时间戳**永远不会**——它在任何一天看都是"10 分钟前"。

### 二、4 个挂着「较上月」的硬编码同比

`DashboardPage.tsx:654 / 664 / 674 / 684` 的 `trend={{ value: 5 | 8 | 12 | 3 }}`。后端**不下发任何环比字段**——`DashboardStats.Summary` 只有 6 个字段（`backend/src/main/java/com/crm/dto/stats/DashboardStats.java`），没有一个叫环比。这四个百分比是字面量，而它们渲染出来的样子（`↑ 5% 较上月`，`DashboardPage.tsx:93-97`）与真同比**无法区分**。

### 三、006 的三项验收要件从未渲染，而登记表 31/31 全勾

**实测**：`specs/006-sales-dashboard/tasks.md` = **31 done / 0 open**（`grep -c "^- \[x\]"` / `"^- \[ \]"`）。而同一目录的 `spec.md` 要求：

| 要件 | 坐标 | 现状 |
|---|---|---|
| US2 情形 1 | `spec.md:39`「**Given** 已设置月度目标，**When** 查看**预测卡片**，**Then** 展示加权预测总额与达成率百分比」 | **零渲染** |
| FR-D07 | `spec.md:95`「必须展示客户分析：客户总数、活跃客户数（ACTIVE）、本月新增客户数」 | **零渲染**（只有 i18n 键 `pages.dashboard.customerAnalysis.*`，无组件） |
| FR-D08 | `spec.md:96`「必须展示跟进活动报表：跟进总数、按方式分布、最近跟进记录列表」 | **零渲染** |
| FR-D02 | `spec.md:90`「核心指标卡：商机总数、金额合计、**赢单率**、本月新增客户数」 | 页面是「客户总数 / 活跃商机 / 金额合计 / 本月新增」—— 赢单率**缺位** |
| SC-D02 | `spec.md:116`「指标卡数值与对应列表页数据一致率 100%」 | 被上一条「二」直接违反 |

**FR-D02 还有一处更隐蔽的错**：第二张卡的标签用 `statCards.activeOpportunities`（「活跃商机」），值却是 `summary.opportunityCount`——而后端 `DashboardStatsService.java:123` 的 `long count = allSo.size()` 是**全部**商机数。「活跃商机」与「商机总数」不是一回事，**标签与值对不上**。

### 四、数据早就在下发，前端一个字段都没消费

这是本项最省力的部分：

| 数据 | 后端已装配 | 契约 | TS 类型 |
|---|---|---|---|
| `forecast`（加权总额 + 分阶段分解 + `probabilitySource`） | `DashboardStatsService.java:114` → `computeForecast` `:188-215` | `006/contracts/stats.md` | `types/stats.ts:36-48` |
| `followUps`（`total` + `byMethod` + `recent`） | `DashboardStatsService.java:116` → `computeFollowUps` `:272-315` | 同上 | `types/stats.ts:60-78` |
| `summary.winRate` | `DashboardStatsService.java:136` | 同上 | `types/stats.ts:18` |
| 客户三项 | `DashboardStatsService.java:142-153` | 同上 | `types/stats.ts:19-21` |

**零命中证据**：`data.forecast` / `data.followUps` 在 `frontend/src` 全域**无匹配**（含 `DashboardPage.tsx` 自身）。`winRate` 在 `frontend/src` 的**唯一**出现是 `types/stats.ts:18` 的类型声明。类型写全了、接口下发了、契约写了——**只是没人渲染**。

### 成因：未立项的追加功能用假数据顶替

`pages.dashboard` 的 i18n 里有**四组设计完整、从未被渲染**的键（`forecast` / `customerAnalysis` / `followUpActivity` / `recentFollowUp`，`zh-CN.ts:881-917`），而实际渲染的两块（`todo` / `activity`，`zh-CN.ts:930-949`）在 006 里**没有任何验收要件**。

即：**规格要求的没做，规格没要求的用假数据做了**。这是「登记表的 ✅ 不等于做了」的又一例。

`specs/roadmap.md:160` 同样已把「客户分析/跟进报表」写进 006 的交付描述——**两处登记都提前于实现**。

---

## 用户场景与测试 *（必填）*

### 用户故事 1 - 卡上的数字都能在库里找到出处（优先级：P1）

销售主管打开 `/stats`。改造前他看到 4 个带「较上月 ↑5%/↑8%/↑12%/↑3%」的指标卡与两块"待办/最近活动"——**其中每一处都无法追溯到库里任何一行数据**，而它们与真实数字外观完全一致，因此他没有任何办法察觉。改造后，卡上每个数字都能在对应列表页对上一个同样的数，且**界面上不再存在任何编造的内容**。

**验收**：商机总数 ↔ `/opportunities` 的 total；金额合计 ↔ 商机金额之和；赢单率 ↔ 已赢/已关闭；本月新增客户 ↔ `/customers`；四个同比消失；两块伪造卡片消失。

### 用户故事 2 - 规格要求的预测与客户分析真正可看（优先级：P1）

主管要知道「按概率折算后的管道值是多少、每个阶段贡献多少、概率是真校准出来的还是拍脑袋的默认值」。改造前他对这些**一无所知**——后端算好了、接口下发了，页面上没有。改造后预测卡展示加权预测总额 + 分阶段分解（阶段 / 金额 / 概率 / 加权额 / **概率来源**）；客户分析卡展示客户总数 / 活跃客户 / 本月新增。

**验收**：`forecast.breakdown[]` 逐行可见；`probabilitySource` 的三个取值（HISTORICAL / DEFAULT / FIXED）各自有可读标注；客户三项与 `summary` 一致。

### 用户故事 3 - 跟进活动报表可用（优先级：P2）

客服/销售经理要看「团队跟进得多不多、都走什么方式、最近谁跟进了谁」。改造前他只能看四行「张三创建了新客户"XYZ 集团"」这类**编造的**动态。改造后看到真实的跟进总数、按方式分布、最近跟进记录列表（客户 / 内容 / 跟进人 / 时间）。

**验收**：`followUps.total` / `byMethod[]` / `recent[]` 逐项可见；空数据时显示空状态而不是空白。

### 边缘场景

- **空管道**：无商机 ⇒ 预测卡 `breakdown` 为空 ⇒ 显示空状态（不是空白、不是 0 行表格）。
- **无跟进记录**：`byMethod` 与 `recent` 均为空 ⇒ 两处各自显示空状态。
- **`winRate` 分母为 0**：无已关闭商机 ⇒ 后端给 `0`（`DashboardStatsService.java:136` 的 `closed == 0 ? 0d`）⇒ 页面显示 `0.0%`，**不得**显示 NaN。
- **未知跟进方式码**：`byMethod` 若出现字典外的方式 ⇒ 回退显示原始码，**不得**静默归并成「其他」（那会让两种不同的方式看起来是同一种）。

---

## 功能需求 *（必填）*

- **FR-001**: 核心指标卡必须按 FR-D02（`006/spec.md:90`）的**四项**呈现：商机总数、金额合计、赢单率、本月新增客户。
- **FR-002**: 商机总数卡的标签必须与值同义。值取 `summary.opportunityCount`（全部商机数），故标签取 `statCards.opportunityCount`；**不得**再使用 `statCards.activeOpportunities`。
- **FR-003**: 赢单率取 `summary.winRate`（0..1 小数），按百分比一位小数渲染。
- **FR-004**: **移除全部四个硬编码同比**，并同时移除 `KpiCard` 的 `trend` prop 及其渲染块（不保留无人调用的死 prop）。
- **FR-005**: 必须渲染成交预测卡，内容为 `forecast.weightedAmount`（走 `formatAmount`，分转元）+ `forecast.breakdown[]` 的分阶段分解（阶段 / 金额 / 概率 / 加权额 / 概率来源）。
- **FR-006**: 预测卡必须**显式标注口径**为「全部在途商机，不含月度目标」。理由见「非目标」第 2 条：`computeForecast` 聚合**全部**商机（`DashboardStatsService.java:101-110` 不带月份过滤），而 `performance.targetAmount` 是**月度**目标——**跨口径相除本身又是一处名实不符**，故本卡**不渲染达成率**。
- **FR-007**: 概率来源必须按 `probabilitySource` 三值分别标注，且 `FIXED` 分支（终态，`DashboardStatsService.java:218-223`）**不得**被漏掉或误标为 HISTORICAL/DEFAULT。
- **FR-008**: 必须渲染客户分析卡：客户总数 / 活跃客户 / 本月新增，三项取自已加载的 `summary`，**不发起额外请求**。
- **FR-009**: 必须渲染跟进活动报表：`followUps.total` + `followUps.byMethod[]` 分布。
- **FR-010**: 方式码映射必须大小写不敏感（后端下发大写 `PHONE/EMAIL/MEETING/OTHER`，i18n 键 `pages.dashboard.method.*` 是小写），且**未知码回退原值**。
- **FR-011**: 必须渲染最近跟进列表：`followUps.recent[]`，列取 `recentFollowUp.*` 的六项；`customerName` / `followUpBy` 可空 ⇒ 渲染 `-`。
- **FR-012**: **删除**两块伪造卡片（`todos` / `activities` 两个 `useMemo` + `TodoList` / `ActivityFeed` 两个组件 + 承载它们的两个 `<Col>`）。
- **FR-013**: 删除 i18n 中与**被删代码 1:1 对应**的键：`pages.dashboard.todo.*`、`pages.dashboard.activity.*`、`home.trendVsLastMonth`、`statCards.totalCustomers`、`statCards.activeOpportunities`。删除后 `statCards` **恰好剩 FR-D02 的四个键**。**两个语言文件必须同步**。
- **FR-014**: 新增 i18n 键必须同时进 `zh-CN.ts` 与 `en.ts`，且与组件**同一次提交**（`src/test/setup.ts:107-113` 的 mock 在缺 zh-CN 键时抛错）。
- **FR-015**: 空 `breakdown` / 空 `byMethod` / 空 `recent` 三处都必须有空状态。

### 顺带修复的两处既有缺陷

- **FR-016**: `DashboardPage.test.tsx:3-9` 的 file-local `vi.mock('react-i18next')` **遮蔽**了 `src/test/setup.ts:91-123` 的校验版 mock——前者 `t: (key) => key` **不查键**，而后者同样返回 key 但**键缺失时抛错**。删除该 file-local mock，使本文件继承校验版。**约束**：既有断言（全部断言 key 字符串）必须逐条不变——两版 mock 对**存在**的键返回同一个值。
- **FR-017**: `README.md:43` 与 `PROJECT_FEATURES.md:36` 的仪表盘功能描述必须与实际渲染一致。现状「KPI 指标、销售漏斗、客户分析、业绩趋势」有两处失实：**业绩趋势**在全仓不存在；**客户分析**在 093 之前只有 i18n 键、无组件（写下它是一处「从键的存在推断已实现」的错误）。正确表述为「KPI 指标、销售漏斗、成交预测、客户分析、跟进活动」。

---

## 成功判据 *（必填）*

- **SC-001**: 仪表盘上**不存在**任何无法追溯到后端下发的显示内容。具体：`home.trendVsLastMonth` 字面量不再被渲染；`pages.dashboard.todo.title` 与 `pages.dashboard.activity.title` 不再被渲染。
- **SC-002**: 006 的 US2 情形 1（预测卡）、FR-D07（客户分析）、FR-D08（跟进活动报表）**三条都有对应渲染**；FR-D02 的四项在指标卡行。
- **SC-003**: `statCards` 在 i18n 中恰好剩 4 个键，与 FR-D02 的四项一一对应。
- **SC-004**: 006 的账有据可查：其 `tasks.md` 有一处带日期的订正段说明哪几条验收要件在 093 之前无渲染；其 `contracts/stats.md` 的 forecast 段不再声称「概率固定配置 20%/50%/100%/0%」（该口径已被 `specs/019-*` 的历史校准取代，见 `FR-D06` 与 `StageConversionService.probabilityFor`）。
- **SC-005**: 每一条新断言都**被观察到能变红**——把负向断言改成正向会失败。**只写"应该没有"而没验过它会红，等于没写。**
- **SC-006**: 七道前端门禁 + `test:coverage` 全过；后端 `mvn -B verify` 结论与本批次之前**逐字相同**（本项零后端改动，这是反证）。
- **SC-007**: 人工把卡上每个数字与对应列表页对一遍，逐项一致（SC-D02 要求的正是这件事，且它**只能是人工判据**）。

---

## 关键实体

本项**不新增任何实体、不新增任何字段、不改任何迁移**。消费的既有实体（全部只读）：

- **`DashboardStats.Summary`**（`dto/stats/DashboardStats.java`）：`opportunityCount` / `amountTotal` / `winRate` / `customerCount` / `activeCustomerCount` / `newCustomersThisMonth`
- **`DashboardStats.Forecast`** + **`ForecastItem`**：`weightedAmount` / `breakdown[]{stage, amount, probability, weighted, probabilitySource}`
- **`DashboardStats.FollowUps`** + **`MethodStat`** + **`RecentFollowUp`**：`total` / `byMethod[]{method, count}` / `recent[]{id, method, content, customerName, followUpBy, createdAt}`

---

## 非目标（明确划出）

1. **给「待办事项」接真实数据**。三个候选源已逐个核查，**无一能低成本渲染**：`GET /approvals/todos`（`ApprovalController.java:76-80`）返回的 `ApprovalTask` **无 `title` 字段、无截止日**（标题在另一张 `approval_instance` 表上，无 join），只有 `instanceId` + `nodeName`；`GET /tasks?status=TODO`（`TaskController.java:55-65`）按 `id DESC` 而非到期日排序（`TaskService.java:58`），取前三条是**任意的**而非最紧急的；`follow_up` 表**没有 `status`/`completed` 列**（`V1__init.sql:77-90` 起即如此），「待跟进」这个语义在数据层不存在。后端也没有任何工作台聚合端点（`/api/v1/tasks/my`、`/api/v1/dashboard/todos` 均**零命中**）。⇒ 需新聚合端点或给 `ApprovalTask` 加字段，**独立立项**。
2. **给预测加月度口径 / 渲染 US2 的「达成率百分比」**。`SalesOpportunity` **没有「预计成交日期」字段**（`DashboardStatsService.java:104-110` 选取的列里没有），open 商机的 `closedAt` 为 `null` ⇒ **跨月预测当下不可算**。需加列 + 迁移 + 契约变更。且达成率本就是相邻「业绩达成」卡的职责（`computePerformance(month)` 已是月度口径）。
3. **`funnel.{count,amountSuffix,conversion,share}`、`buttons.mobile*` 等其它孤儿 i18n 键**。它们不面向用户（不会被渲染成任何东西），且与本次删除的代码不是 1:1 关系。本项**只删与「被删代码 1:1 对应」的键**。
4. **`AnnouncementCard` / `FunnelChart` / `PerformanceCard` / `StalledTable` 的内部逻辑**。本轮只动卡位与编排，不动这四个组件内部。
5. **088 的 P3/P4**（`FormModal`/`FormGrid` 铺开、`index.css` 清理、`AmountDisplay`/`StatusTag` 全库替换）。平行的另一个批次。本项**不碰**本页 `:901` 的 `layout="vertical"` 与 `:899` 的 `width={480}`——那两处正交于本项。

---

## 机器门禁约束（本项必须同时满足，逐条实测）

| 门禁 | 约束 | 本项动作 |
|---|---|---|
| `scripts/check-perms.mjs:89-95` | 本文件的 `role === 'ADMIN'` 必须**恰好 1 次**（按文件 + 命中数**双向**校验） | **不动**：`isAdmin` 仍被 `PerformanceCard` 的「设置目标」按钮使用（`:787`）。**绝不新增第二个角色判断** |
| `scripts/check-ui.mjs:296-302` | 本文件的品牌色字面量命中数登记为 **7**（R1 白名单，双向校验） | 删除 `ActivityFeed` 至少去掉 `:438` 一处 ⇒ 命中数**必变**。**改完跑 `pnpm ui:check` 读出实测值再改 `count`**，并同步订正 `reason` 使其仍然准确 |
| `scripts/check-ui.mjs` R3 | 所有 `<Col>` 必须带响应式断点，零容忍、无白名单 | 新卡位的 `<Col>` 一律写 `xs={24} lg={12}`（照本文件既有 8 处写法） |
| `scripts/check-i18n.mjs:58-72` | zh-CN 与 en 的键集合**双向必须相同** | 7 删 + 7 增，两个文件同步 |
| `src/test/setup.ts:107-113` | 缺 zh-CN 键即抛（FR-016 之后本页也吃上这条） | 新键必须同时进两个语言文件 |
| `vite.config.ts:102-107` | 阈值 33.6 / 47.2 / 21.4 / 33.6，**不得下调**（084 T037） | 纯增删渲染，余量大；跑一次确认 |

---

## 风险

| 风险 | 缓解 |
|---|---|
| **改动撞上并行会话**：本文件最近一次改动是 088 的 `f38b17d`（T040 的 R2 宽度清洗），且 088 **未勾选的 T041**（其余 `layout="vertical"` 表单，明文「按文件聚合执行」）很可能把本文件算在内——`:901` 正是这样一处 | 开工前 `ListAgents` 确认 088 会话已收工；跑全仓门禁前再确认一次无人在写 `src/pages/**`。本项改动与 T041 的落点**正交**（不动 `layout` / `width`） |
| **`ui:check` R1 白名单陈旧** | 命中数必变。**读出实测值再改**，不预测；双向校验会让陈旧条目立刻转红，不会静默 |
| **跨口径相除的重演** | FR-006 的口径标注不是装饰，它是本项最重要的判断的落地形式。后来者若「顺手」加上 `weightedAmount / targetAmount`，就在一次以名实相符为名的批次里造出了新的名实不符 |
| **删 i18n 键漏了一个语言文件** | `i18n:check` 双向校验 + `setup.ts` 缺键抛错（FR-016 之后本页也吃得上）双保险 |
| **`006/tasks.md` 的 31/31 是「做了但走得早」还是「回填」** | **无法从勾选行分辨**。故订正段**只陈述实测事实**（哪三条验收要件在 093 之前无渲染），**不对历史动机下判断** |
| **把「删掉假数据」误读成「功能减少」** | 删掉的两块在 006 里**没有任何验收要件**，且其内容全部是编造的。本项同时补上 006 要求的三项——**净增功能，不是净减** |
