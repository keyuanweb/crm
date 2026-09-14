# 可证伪留痕：几何护栏**会红**（092 SC-EG-006 / FR-EG-014）

> **本文的判据**：**只报「新增 X 条用例、全绿」不构成证据。**
> 「跑绿」与「真的在断言」在本仓是反复栽过的两件事——091 的窄屏分支在**全站内容宽 0** 时全量测试全绿
> （断言全落在文案上），048 的 e2e 套件 22/23 用例**从未被执行过**却长期存在。
> 故本文记录的是**三次定向破坏 + 一次活性核对**：破坏之后用例**确实红了**，红的是**它声称的那一条**，
> 且还原后**转绿**。

**日期**：2026-09-14 ｜ **本机**：Windows 11，Playwright chromium（覆盖式滚动条），dev server `http://localhost:5173`

---

## 0. 环境与基线（改动前）

**后端`vintage`核对**（本仓规矩：e2e 打的是「已经在跑的后端」，不一定是本次改动）：

| 项 | 值 |
|---|---|
| 后端进程 PID / 启动时间 | 20360 / **2026-09-13 23:24:02** |
| `backend/target/classes` 下最新 `.class` 的 mtime | **2026-09-13 23:12:00** |
| 结论 | 进程**晚于**全部产物 ⇒ 与当前 `.class` 同版。**本项判据是纯前端的**，后端版本不影响几何读数，此表仅为如实标注 |

**基线**（三项，后面每一次「变红／转绿」都与它对拍）：

| 项 | 命令 | 结果 |
|---|---|---|
| 单元测试 | `cd frontend && pnpm run test` | **83 文件 / 378 用例 全过**，exit 0，69.91s |
| 既有 e2e（5 个文件） | `cd frontend && pnpm run test:e2e -- --reporter=list` | **51 passed**，exit 0，47.2s |
| 新增 e2e（本项 2 个文件 / 7 条用例） | `cd frontend && npx playwright test geometry --reporter=list` | **7 passed**，12.3s |

新增用例的实测读数（与 `research.md` 一致，可作对照）：

```
[092][列表页] /quotas        行数=1  溢出=0   fill=0        residual=0        卡片=pro-table(底边 663.63 / 下外边距 0)
[092][列表页] /orders        行数=2  溢出=0   fill=0        residual=0        卡片=pro-table(底边 663.63 / 下外边距 0)
[092][列表页] /data-retention 行数=1 溢出=0   fill=0        residual=0        卡片=card(底边 647.63 / 下外边距 16)
[092][列表页] /departments   行数=0  溢出=0   fill=0        residual=0        卡片=card(底边 647.63 / 下外边距 16)
[092][列表页] /users         行数=20 溢出=365 fill=-365.75  residual=-365.75  卡片=pro-table
[092][列表页] /customers     行数=20 溢出=415 fill=-415.75  residual=-415.75  卡片=pro-table
[092][分类] 短页 4 页：/quotas, /orders, /data-retention, /departments ｜ 长页 2 页：/users(溢出 365, 20 行), /customers(溢出 415, 20 行)
[092][窄屏] /stats      内容区可见宽=375 内容容器宽=351 内容区左右内边距=12/12 菜单=375×48 横向溢出=0 锚点=OK
[092][窄屏] /customers  内容区可见宽=375 内容容器宽=351 内容区左右内边距=12/12 菜单=375×48 横向溢出=0 锚点=OK
[092][窄屏] /orders     内容区可见宽=375 内容容器宽=351 内容区左右内边距=12/12 菜单=375×48 横向溢出=0 锚点=OK
[092][窄屏] /roles      内容区可见宽=375 内容容器宽=351 内容区左右内边距=12/12 菜单=375×48 横向溢出=0 锚点=OK
```

**共用的还原纪律**（三次破坏都遵守）：破坏前把目标文件按字节备份到仓库外
（`/tmp/index.css.orig`、`/tmp/App.tsx.orig`），还原时**整file覆盖**而不是反向编辑——
反向编辑容易在空白/换行上留下看不见的差异。每次还原后同时核对 **md5 与 `git diff`**。
**破坏期间未提交任何东西。**

---

## 1. 破坏 ①：注释掉「列表页撑满」**入口 A**（ProTable 根）

**位置**：`frontend/src/index.css`（`.page-fade > .ant-pro-table,` 起的那条规则块）
**改法**：整条规则块用 `/* … */` 注释掉，仅此一处
**预期**：短页 `fill == 0` 在 **ProTable 桶**上失败，**Card 桶仍绿**

**破坏后**（`npx playwright test geometry-list-page --reporter=list`）：

```
Error: [/quotas] fill = 滚动盒底边 − 自身下内边距 − 卡片底边 − 卡片下外边距：实测 164.58，应达 0（±1px），实际偏差 164.58px
Error: [/quotas] residual = (视口底边 − 卡片底边) − (页脚实测高 + 内容区下内边距 + 卡片下外边距)：实测 164.58，应达 0（±1px），实际偏差 164.58px
Error: [/orders] fill = 滚动盒底边 − 自身下内边距 − 卡片底边 − 卡片下外边距：实测 246.25，应达 0（±1px），实际偏差 246.25px
Error: [/orders] residual = (视口底边 − 卡片底边) − (页脚实测高 + 内容区下内边距 + 卡片下外边距)：实测 246.25，应达 0（±1px），实际偏差 246.25px

  1 failed
  2 passed (12.3s)
```

- 红的是 **`/quotas` 与 `/orders`**——正是**入口 A** 的两个样本；`/data-retention`、`/departments`（入口 B）**一条错都没报**。
- 「长页」与「横向溢出」两条用例仍绿（符合预期：入口 A 只影响短页的吃满）。

**还原后**：`md5 = 0d0c42686e76722f978471c9b80c8c1d`（与破坏前一致）、
`git diff -- frontend/src/index.css` **为空** → 复跑 **3 passed (11.6s)**。

---

## 2. 破坏 ②：注释掉同段**入口 B**（Card 根）

**位置**：`frontend/src/index.css`（`.page-fade > .ant-card:has(.ant-table, .ant-tree, .ant-list)` 那条）
**改法**：同上，整条注释掉
**预期**：短页 `fill == 0` 在 **Card 桶**上失败

**破坏后**：

```
Error: [/data-retention] fill = 滚动盒底边 − 自身下内边距 − 卡片底边 − 卡片下外边距：实测 301.25，应达 0（±1px），实际偏差 301.25px
Error: [/data-retention] residual = (视口底边 − 卡片底边) − (页脚实测高 + 内容区下内边距 + 卡片下外边距)：实测 301.25，应达 0（±1px），实际偏差 301.25px
Error: [/departments] fill = 滚动盒底边 − 自身下内边距 − 卡片底边 − 卡片下外边距：实测 223.63，应达 0（±1px），实际偏差 223.63px
Error: [/departments] residual = (视口底边 − 卡片底边) − (页脚实测高 + 内容区下内边距 + 卡片下外边距)：实测 223.63，应达 0（±1px），实际偏差 223.63px

  1 failed
  2 passed (12.2s)
```

- 红的是 **`/data-retention` 与 `/departments`**——正是**入口 B** 的两个样本；`/quotas`、`/orders`（入口 A）**一条错都没报**。

> **① 与 ② 的失败集合互补**（`{/quotas, /orders}` ↔ `{/data-retention, /departments}`），
> 这正是 FR-EG-013 要求的「**能定位到具体页面 + 具体量**」——失败信息指出的是**哪几页**，
> 而不是笼统的「几何不对」。**这也是为什么两次破坏必须分开做**：合并成一次改两处，就看不出这个互补。

**还原后**：md5 与破坏前一致、`git diff` 为空 → 复跑 **3 passed (11.6s)**。

---

## 3. 破坏 ③：窄屏照样渲染侧边栏（091 自己用过的那一次）

**位置**：`frontend/src/App.tsx` 的 `{isMobile ? (` → `{false ? (`（多加了注释标记，仅此一处）
**预期**：窄屏「内容区可见宽 = 视口宽」失败（退回 **24**）

**破坏后**（`npx playwright test geometry-narrow-shell --reporter=list`）：

```
[092][窄屏] /stats      内容区可见宽=24 内容容器宽=0 内容区左右内边距=12/12 菜单=199×200 横向溢出=0 锚点=可疑
[092][窄屏] /customers  内容区可见宽=24 内容容器宽=0 内容区左右内边距=12/12 菜单=199×200 横向溢出=0 锚点=可疑
[092][窄屏] /orders     内容区可见宽=24 内容容器宽=0 内容区左右内边距=12/12 菜单=199×200 横向溢出=0 锚点=可疑
[092][窄屏] /roles      内容区可见宽=24 内容容器宽=0 内容区左右内边距=12/12 菜单=199×200 横向溢出=0 锚点=可疑

Error: [/stats] 内容区可见宽度（.page-scroll 的 clientWidth，含其自身左右内边距）：实测 24，应达 视口宽 375（滚动条容差 ±16px），实际偏差 351px
Error: [/stats] 菜单容器锚点：锚点可疑：shellHoldsContent=false insideContent=false shellIsRoot=false（菜单根 <ul class="ant-menu-overflow ant-menu ant-menu-root …">，其父 <div class="ant-layout-sider-children">）
Error: [/stats] 菜单容器宽（= 内容区可见宽，**不是** 内容容器宽）：实测 199，应达 24（±1px），实际偏差 175px
Error: [/stats] 菜单容器高（其声明高度）：实测 200，应达 48（±1px），实际偏差 152px
（/customers、/orders、/roles 三页逐页同样的四条）

  2 failed
  2 passed (12.7s)
```

- 内容区可见宽**恒 24**、菜单容器**恒 199×200**——**这正是 091 修复前的两个缺陷形态**（24px 的缝、200×200 的方块），
  不是近似、不是巧合，是原缺陷被原样复现。
- 锚点断言同时报「可疑」，并把**它认到的对象**打了出来（父节点是 `<div class="ant-layout-sider-children">`）——
  认错对象时**信息是能读的**，不是一个莫名其妙的小数值。

### 3.1 ⚠️ 一处**必须如实记下**的发现：缺陷态下「内容容器宽」那条断言**会通过**

`2 failed / 2 passed` 里的两个 `passed`，其一是
「**内容容器宽度 = 内容区可见宽度 − 24**」（独立断言二）。原因：

| 量 | 缺陷态实测 | 判据 | 结果 |
|---|---|---|---|
| 内容容器宽 | **0** | `= 内容区可见宽 − 24` = `24 − 24` = **0** | **满足** ⇒ 绿 |
| 内容区可见宽 | **24** | `= 视口宽 375` | 24 ≠ 375 ⇒ **红** |

**这是 FR-EG-007 明令「不得只断言两者之差」的实证，不是意外**：
只写第二条，原缺陷会被**原样放过**。两条独立断言里，**抓缺陷的是第一条**，
第二条的作用是**钉住口径**（把 351 这个数钉死，防止 24 被改成别的值而两侧一起漂）。
091 的判据文书正是因为把两者混为一句而订正过一次（见 `specs/091-narrow-shell-collapse/spec.md` 的「订正块」）。

### 3.2 旁证：**结构性单测同时变红**（与 091 的记载一致）

破坏 ③ 期间跑 `npx vitest run src/App.render.test.tsx`：

```
Test Files  1 failed (1)
     Tests  2 failed | 5 passed (7)
AssertionError: expected <div …> to be null            ← .ant-layout-has-sider 出现了
AssertionError: expected <aside …> to be null          ← 侧边栏出现了
```

**两边同时红是好迹象，不是冲突**——但请注意它们的**能力边界**：
单测抓到的是「那个类名/那个元素出现了」（**结构前提**），它**量不了宽度**。
所以只要那个类名不被引入，内容区是 375 还是 24，单测**看不出来**——
**这正是本项存在的理由**，不是重复劳动。

**还原后**：`md5 = 087ac8fe5cb970509411c67fb2e933f9`（与破坏前一致）、
`git diff -- frontend/src/App.tsx` **为空** → 复跑 `npx playwright test --reporter=line` →
**58 passed (48.7s)**（= 基线 51 + 本项 7）。

---

## 4. 活性核对：把「跳过」变成「失败」（SC-EG-005 / FR-EG-010）

**与 ①②③ 的区别**：那三次破坏的是**生产代码**；这一次破坏的是**候选清单**——
验的是「**没测到**」本身会不会红。

**改法**：把 `geometry-list-page.spec.ts` 的宽屏候选**全部临时换成长页路径**
（`const CANDIDATES = ['/users', '/customers']`），短页桶的样本数于是归零。

**结果**：

```
Error: 【活性】宽屏短页 样本数为 0（（空）），低于下限 2。
       短页样本归零通常是**数据增长把短页撑成了长页**（预期行为），处置是**增补采样页**，不是放宽判据。

  1 failed
  2 passed (6.2s)
```

- 是**红**，不是静默通过；信息里明确写了**样本数为 0** 与**处置办法**。
- 注意「长页」用例仍绿——因为长页桶这时的样本是 2（≥ 下限 1）。**每类不变式各自计数**，互不掩盖。
- 还原候选清单后复跑：**7 passed (12.3s)**。

---

## 5. 源码审计：不存在「条件不满足就整段不执行」的形态（FR-EG-011）

| 文件 | 行数 | `expect` 出现 | `if (` 出现 |
|---|---|---|---|
| `frontend/e2e/geometry-list-page.spec.ts` | 170 | 12 | **0** |
| `frontend/e2e/geometry-narrow-shell.spec.ts` | 147 | 11 | **0** |
| `frontend/e2e/helpers/geometry.ts` | 460 | 12 | 11 |

- **两个 spec 全文一个 `if` 都没有**——断言不可能被条件包住。
- `helpers/geometry.ts` 里的 11 处 `if` **逐处核对**，全部属于下面三类，**没有一处能包住断言**：
  1. **纯计算函数的守卫**（`if (g.cardBottom === null) return Number.NaN`，2 处）：
     返回 `NaN` 会让 `Math.abs(NaN) <= 1` **不成立** ⇒ **红**。且这两条路径在 spec 侧还先被
     「页面根卡片必须找得到」（`expect.soft(g.cardKind).not.toBeNull()`）拦了一道。
  2. **就绪谓词**（`waitForFunction` 里的 4 处）：它返回 `false` 只意味着**继续等**，
     等到 8s 超时**抛错**⇒ 红。不存在「等不到就往下走」。
  3. **自稳定轮询与锚点判定**（5 处）：轮询超时**抛错**；锚点判定写入 `menuAnchorOk`，
     由 spec 里的 `expect.soft(g.menuAnchorOk).toBe(true)` **断言**（见 §3 的破坏 ③，它真的会红）。
- 全库检索确认：新增三个文件里 **无** `test.skip` / `test.fixme` / `test.only` / `describe.skip` / `describe.only`。

---

## 6. 收尾核对

| 项 | 结果 |
|---|---|
| 三次破坏是否全部还原 | `md5(frontend/src/index.css)` = `0d0c4268…`、`md5(frontend/src/App.tsx)` = `087ac8fe…`，与破坏前一致；两个文件的 `git diff` **均为空** |
| 破坏期间是否提交过东西 | **没有**（`git log` 在破坏期间无新增提交） |
| 全量 e2e（还原后） | **58 passed (48.7s)** = 基线 51 + 本项 7，**无新增失败** |
| 单元测试（还原后） | 见 §7 的四项门禁 |
| 临时探针 | 无（本项未在 `e2e/` 下留任何 `__probe-*`；所有验证都用正式用例 + 临时改动生产代码，且已还原） |

---

## 7. 四项既有门禁（SC-EG-007）

```bash
cd frontend && pnpm run typecheck && npx eslint . && pnpm run test && pnpm run build
```

**交付时现跑的读数**（全部在三次破坏**还原之后**）：

| 门禁 | 命令 | 结果 | 与基线（T002）比对 |
|---|---|---|---|
| 1 | `pnpm run typecheck` | `tsc --noEmit` 无输出，**exit 0** | — |
| 2 | `npx eslint .` | 无输出，**exit 0** | — |
| 3 | `pnpm run test` | **83 文件 / 378 用例全过**，67.42s | 文件数与用例数与基线**逐数一致**（378 ≥ 378，**无减少**） |
| 4 | `pnpm run build` | `✓ built in 10.90s` | 仅既有的大 chunk 警告（`DataVisionPage` 等），**非本项引入** |

- 门禁 3 的 378 与基线完全相同：本项**只新增 e2e 文件，未增删任何单测**，
  故「单测总数不减少」不是靠新增用例凑出来的，而是**一条没动**。
- 门禁 4 的警告在本项之前就存在（`index.css` / `App.tsx` 本次**逐字节未改**，见 §6）。

---

## 附：本项**没有**证明的东西（如实列出）

1. **CI 上跑得通没验过**：本仓无远端、CI **永不触发**（083/T069 的既定口径）。
   本地等效命令即 `pnpm run test:e2e`，与 CI 作业执行的是同一条。
2. **滚动条容差 ≤16px 在 CI 上未实测**：本机实测占位为 0（覆盖式滚动条），16px 是为经典滚动条预留的。
   缺陷态是 24，与此容差相差一个数量级，故它**不会掩盖**本项要防的两个缺陷。
3. **`fill` 的 ±1px 容差是选定的、不是量出来的**（见 `research.md` §8 第 3 条）。
4. **并发跑 e2e 与人工操作同一 dev server 的相互影响未测**：本项用例只读（不点提交、不改数据），
   但登录会写会话；风险低。
