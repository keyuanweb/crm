# 实施计划：仪表盘名实相符（093）

**Input**: [spec.md](./spec.md)（用户故事与 FR 来源）

## 摘要

把 `/stats` 页面渲染的内容收敛到「后端下发的东西」与「006 要求的东西」两个集合的交集上：

1. **删**掉两块伪造卡片（`todos` / `activities`）与 4 个硬编码同比；
2. **补**上 006 三项从未渲染的验收要件（预测卡 / 客户分析卡 / 跟进活动报表 + 最近跟进），全部消费**已在响应里**的 `forecast` / `followUps` / `summary`；
3. **记**清 006 的账（`tasks.md` 订正段 + `contracts/stats.md` 口径订正）。

**零后端改动、零 DTO 改动、零迁移、零契约变更**——`types/stats.ts` 的消费侧类型（`ForecastItem.probabilitySource`、`MethodStat`、`RecentFollowUp`）**已经写全**，本项是**补渲染**，不是补接口。

## 技术上下文

| 项 | 值 |
|---|---|
| 语言/框架 | TypeScript 5 / React 18 / antd 5.22 / React Query 5 / react-i18next |
| 目标文件 | `frontend/src/pages/stats/DashboardPage.tsx`（953 行） |
| 测试文件 | `frontend/src/pages/stats/DashboardPage.test.tsx`（152 行，5 例） |
| i18n | `frontend/src/i18n/zh-CN.ts` / `en.ts`（各 3348 行） |
| 复用原语 | `formatAmount`（`types/opportunity.ts:72-75`，分转元）、`stageLabel`（`hooks/useOpportunityStages.ts`，阶段名单一口径）、`renderWithProviders`（`test/renderWithProviders.tsx`） |
| 不引入 | **零新依赖**、零新组件文件（四张卡都是本页 in-file 子组件，照 `KpiCard`/`FunnelChart`/`StalledTable` 既有形态） |

## 结构决策

### 决策 1：新卡片放本页 in-file，不抽 `components/`

本页已有的子组件（`KpiCard` `:58-117`、`FunnelChart` `:120-254`、`PerformanceCard` `:257-386`、`TodoList` `:389-421`、`ActivityFeed` `:424-453`、`StalledTable` `:923-953`）**全部在本文件内定义**。四张新卡只在 `/stats` 用一次，抽到 `components/` 会制造 4 个单调用点的文件，且每个新文件都要配测试才能不压穿 `functions` 阈值（`vite.config.ts:102-107` 的 21.4）。**照既有形态留在本文件内**。

### 决策 2：删除的是组件级代码，不是注释级

`TodoList`（`:389-421`）与 `ActivityFeed`（`:424-453`）是**组件定义**，连同两个 `useMemo`（`:529-542`）与两个承载 `<Col>`（`:798-850`）一并删除——**不保留「以后可能用得上」的注释块**。理由是它们的存在本身会让下一个人以为后台有对应数据源。

### 决策 3：版式（行序按重要性，不按历史位置）

```
KPI 行      商机总数 | 金额合计 | 赢单率 | 本月新增客户        ← FR-D02，同比全删
AI 建议条   （不动）
第 1 行     销售漏斗 | 业绩达成                              （不动）
第 2 行     成交预测 | 客户分析                              ← US2/FR-D06、FR-D07
第 3 行     跟进活动 | 最近跟进                              ← FR-D08
第 4 行     停滞商机 | 团队公告                              （不动）
生成时间    （不动）
```

第 2 行两列取代原「待办 | 活动动态」；第 3 行是**净增**一行。四个新增卡位全部 `xs={24} lg={12}`（满足 R3）。

### 决策 4：`forecast.breakdown` 用 `Table` 而非自绘

`breakdown` 是「阶段 / 金额 / 概率 / 加权额 / 来源」五列，`StalledTable`（`:923-953`）已是本页 `Table` 的现成形态（`size="small"`、`pagination={false}`、`rowKey="stage"`）。照它写，不自绘。

### 决策 5：方法码映射大小写不敏感 + 未知码回退原值

后端 `byMethod` 的 `method` 是大写（`PHONE/EMAIL/MEETING/OTHER`），i18n 键 `pages.dashboard.method.*` 是小写。映射写成：

```ts
const methodLabel = (m: string) => {
  const key = `pages.dashboard.method.${m.toLowerCase()}`
  const label = t(key)
  // t() 对缺键返回 key 本身（setup.ts 的 mock 在缺键时抛错，故生产环境走兜底）
  return label === key ? m : label
}
```

**不写 `?? 'other'`**——那会把未知方式静默归并成「其他」，让两种不同的方式看起来是同一种。

## 分阶段

| 阶段 | 内容 | 出口判据 |
|---|---|---|
| **P1 i18n**（先行） | 删 7 组键、增 7 个键，**两个语言文件同步** | `pnpm i18n:check` 绿 |
| **P2 页面** | KPI 行改造 + 4 张卡 + 删伪造块 | `pnpm typecheck` 绿、页面能起 |
| **P3 测试** | 6 条新断言（含负向与空态）+ 删 file-local mock | `pnpm test src/pages/stats/DashboardPage.test.tsx` 绿 |
| **P4 门禁** | 读 `ui:check` 实测值改白名单 `count`；七道门禁全跑 | 七道全绿 |
| **P5 文档** | 006 订正段、README/PROJECT_FEATURES、`specs/README.md` 表 + `roadmap.md` 进度行 | — |

P1 必须在 P2 之前：删键会让页面**编译期无感、运行期抛错**（`setup.ts:107-113`），顺序反了会在 P2 期间反复看到假红。

## 逐文件改动清单

| 文件 | 改动 |
|---|---|
| `frontend/src/i18n/zh-CN.ts` | 删 `:930-937` `todo.*`、`:938-949` `activity.*`、`:103` `trendVsLastMonth`、`:846` `statCards.totalCustomers`、`:847` `statCards.activeOpportunities`；`forecast` 块（`:881-886`）增 4 键；`followUpActivity` 块（`:904-908`）增 1 键 |
| `frontend/src/i18n/en.ts` | 同上一一对应（键名相同、值英文） |
| `frontend/src/pages/stats/DashboardPage.tsx` | 见下 |
| `frontend/src/pages/stats/DashboardPage.test.tsx` | 删 `:3-9` file-local mock；`:97` 的 `statCards.totalCustomers` 改为 `statCards.winRate`；新增 6 组断言 |
| `frontend/scripts/check-ui.mjs` | `:296-302` 的 R1 条目 `count` 按**实测值**订正 + `reason` 同步 |
| `README.md` | `:43` 仪表盘描述 |
| `PROJECT_FEATURES.md` | `:36` 仪表盘描述；§九 增一条 |
| `specs/006-sales-dashboard/tasks.md` | 末尾**追加**带日期订正段 |
| `specs/006-sales-dashboard/contracts/stats.md` | forecast 段口径订正 + 补 `probabilitySource` |
| `specs/README.md` | 模块表增 093 行；`:3` 的形制提要补一句 |
| `specs/roadmap.md` | `## 当前进度`（`:253` 之后）增 093 行；`:4` 最后更新 |

### `DashboardPage.tsx` 内部

| 位置 | 改动 |
|---|---|
| 导入（`:1-44`） | 删 `Timeline`（仅 `ActivityFeed` 用）、按需增图标；`useMemo` 视删除后是否仍被使用决定去留 |
| `KpiCard`（`:58-117`） | 删 `trend` prop 与 `:93-97` 的渲染块 |
| `TodoList`（`:389-421`） | **整块删除** |
| `ActivityFeed`（`:424-453`） | **整块删除** |
| 新增 `ForecastCard` | 紧随 `PerformanceCard` 之后 |
| 新增 `CustomerAnalysisCard` | 同上 |
| 新增 `FollowUpActivityCard` | 同上 |
| 新增 `RecentFollowUpTable` | 同上 |
| `DashboardPage` 主体 | 删 `todos`（`:529-534`）与 `activities`（`:536-542`）两个 `useMemo`；取 `const fc = data?.forecast`、`const fu = data?.followUps` |
| KPI 行（`:647-686`） | 按 FR-001/002/003/004 重排 |
| 第 2 行（`:798-850`） | 两列替换为 `ForecastCard` / `CustomerAnalysisCard` |
| 第 3 行（新增） | 插在 `:851` 的 `</Row>` 之后、`:854` 的第 4 行之前 |

**锚点纪律**：所有坐标引用以**符号名**（`TodoList`、`const todos`）为准，行号仅作参考——本文件正被 088 会话可能再次改动（见 spec.md 风险表第 1 条）。

## 验证

```bash
cd frontend
pnpm typecheck && pnpm lint && pnpm i18n:check && pnpm menu:check && pnpm perms:check && pnpm ui:check && pnpm test:coverage
```

```bash
cd backend && mvn -B verify    # 反证：结论应与本批次之前逐字相同（零后端改动）
```

**人工判据（SC-007，只能是人工的）**：`pnpm dev` + admin 登录 `/stats`，把卡上每个数字与对应列表页对一遍。

**护栏自证会红（SC-005）**：把任一条负向断言改成断言「存在」，确认测试**变红**后还原。只写「应该没有」而没验过它会红，等于没写。
