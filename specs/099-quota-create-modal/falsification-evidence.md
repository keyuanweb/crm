# 定向破坏与订正留痕（099）

**日期**：2026-09-16 · **配套**：[`quickstart.md`](./quickstart.md) · [`plan.md`](./plan.md)

> **本件在立项阶段是骨架**：§0 的**改动前基线**已实测可采信；§A–§G 的**破坏观测**、§H 的**门禁读数**、
> §I 的**订正自查**留到交付时填，以免把未实测的数字写进来。**交付时每节都必须有真实输出**，缺一节即视为**该条护栏未验证**。
>
> ✅ **2026-09-16 交付时已全部填实**：§A–§G 的 **D1–D7 逐条实跑并逐条还原**（还原判据见各节，两个被破坏过的文件
> 均以 `git hash-object` == `git rev-parse HEAD:<path>` 核对，`git status --porcelain` 无破坏残留）；
> §H 的八道门禁 + 覆盖率 + 只读冒烟 + e2e **均为实跑读数**；§I 的五条 grep 自查**均非零**；§J 六条边界**逐条复核**。
> ⚠️ **上面那句「本件在立项阶段是骨架」原文保留**（写下时它是事实）——本行是对它的**交付态**补充，不是改写。

---

## §0 改动前基线（2026-09-16 本项开工前实跑，**工区干净**）

```bash
cd frontend && pnpm i18n:check && pnpm menu:check && pnpm perms:check && pnpm ui:check && pnpm zh:check
```

| 门禁 | 改动前读数（原样） | 本项预期变化 |
|---|---|---|
| `i18n:check` | `zh-CN 2963 键 / en 2963 键；菜单路由与清单双向对齐（路由 58 条 / 清单 56 项，粗粒度别名 3 条）` | **键 2963 → 2962**（删 `btnBack`）；**路由仍 58**、清单仍 56（见 `research.md` §3） |
| `menu:check` | `✓ 菜单清单是最新的（56 个菜单项，来源：RoleConstants.MENU_TREE）` | **不变** |
| `perms:check` | `✓ 权限判定接线校验通过（68 个权限码；8 个文件含已登记的 ADMIN 判断，共 9 处）` | **不变** |
| `ui:check` | `扫描 272 个产品文件（其中 126 个 tsx）、304 个 Form.Item` / `白名单内冻结的既存债 54 处，未新增违规` | **文件 272 → 271**、`Form.Item` **304 → 303**（⚠️ **实做订正 2026-09-16**：立项期推算写的是 `304 → 298` —— **实测是 303**。差在**删掉那一页的 `<Form.Item>` 开标签数是 7 不是 6**（6 个字段 + **1 个包按钮的** `Form.Item`），而弹窗里只有 6 个字段 ⇒ `304 − 7 + 6 = 303`，与 `pnpm ui:check` 打印的 `303 个 Form.Item` 一致。**其余四个数与推算一致**：键 2962、文件 271、`zh:check` 文件 268、Spec 模块 98）；**冻结 54 不变**、`R2` 候选 **55 不变**（用原语） |
| `zh:check` | `扫描 269 个产品文件（排除 src/i18n 与 *.test.*）、候选点 9158 个` / `未登记命中 0 处；台账内冻结 266 处、4 条` / `口径外另有 55 处（全角标点 / 全角字母数字 9、带插值的模板串（字面部分） 46）` | **文件 269 → 268**；**命中与台账不变**（`App.tsx` 的 55 不动）；候选点会**双向变化**（删掉 6 个 `t()` 键名是候选点，新弹窗的 `t()` 也是） |

⚠️ **两个数只动「只印不判」的那一侧**：`ui:check` 的文件数与 `Form.Item` 数、`zh:check` 的文件数**都不参与判定**（`research.md` §3 逐条给了判据）。
⚠️ 覆盖率与 `test:coverage` 的改动前基线**在交付时一并给出**（本项不沿用 098 的历史读数当交付值）。
⚠️ **2026-09-16 交付时补充**：**「改动前基线」这一次没有单独采集**——本项是纯前端页面改造，
覆盖率基线在本项开工前**没有单独跑过一次**（098 交付时的 71.36 / 75.26 / 39.24 / 71.36 是**上一条**的读数，
按本件自己的约定**不当作本项的基线**）。§H 给出的 **72.13 / 75.36 / 39.54 / 72.13** 是**交付时的实测**，
它与阈值（33.6 / 47.2 / 21.4，本项未改）的比法是**高于**，**不是**与某个基线比涨幅
⇒ **本项不声称覆盖率的「提升量」**，只声称「四项均高于阈值且阈值未改」。

---

## §A D1 —— 删 `size="lg"`（弹窗退回默认 `md`）

**破坏**：`frontend/src/pages/quotas/QuotaListPage.tsx` 的 `<FormModal>` 去掉 `size="lg"`。

**期望**：单测 1 的**宽度断言**转红（`.ant-modal` 内联 `style` 的 `width` 由 800 变 640）。

**实际观测（2026-09-16 交付时实跑）**：

```bash
cd frontend && pnpm exec vitest run src/pages/quotas/QuotaListPage.form.test.tsx
```

```
→ expected '640px' to be '800px' // Object.is equality
⎯⎯⎯⎯⎯⎯ Failed Tests 1 ⎯⎯⎯⎯⎯⎯⎯⎯⎯
AssertionError: expected '640px' to be '800px' // Object.is equality
 Test Files  1 failed (1)
      Tests  1 failed | 4 passed (5)
```

**exit 1**；失败点是**单测 1** 的宽度断言（`640` = `md` 档默认值，正是 `size="lg"` 缺席时的取值）
⇒ 这条断言**真的看着**那个档位，不是装饰；另 4 条**照旧全绿**，说明破坏只打中了它该打中的那一处。

**还原判据**：`cp` 备份回写后 `git hash-object frontend/src/pages/quotas/QuotaListPage.tsx` ==
`git rev-parse HEAD:frontend/src/pages/quotas/QuotaListPage.tsx` → **`dc09ea9566e9fde41e9610e57975e84fa1245ec4`**（内容级相等）。

---

## §B D2 —— 删掉 `onSubmit` 里的 `validateFields()`

**破坏**：去掉表单校验那一步（直接组 payload 提交）。

**期望**：单测 2 转红 —— **必填留空时 `quotaApi.create` 被调用**了（校验不再拦截）。

**实际观测（2026-09-16 交付时实跑）**：

**破坏的准确形态**（比计划的一行多两处，如实记）：
`const values = await form.validateFields().catch(() => undefined);` → `.catch(() => form.getFieldsValue())`，
**并把** `(values.periodRange as [Dayjs, Dayjs])[0]` / `[1]` 改成 `?.[0]` / `?.[1]`。
加那两处 `?.` 是**为了不让破坏被一个 `TypeError` 掩盖**——不加，`periodRange` 为 `undefined` 时
代码会在组装 payload 处抛异常，`create` 依旧不会被调用，于是「没调用」那半**假绿**，看不到真正的症状。
（这一步不是可选的洁癖：它决定了这次破坏证的是「校验不再拦截」还是「代码崩了」。）

```bash
cd frontend && pnpm exec vitest run src/pages/quotas/QuotaListPage.form.test.tsx
```

```
→ Unable to find an element with the text: pages.quotaCreate.msgAmountRequired. …
⎯⎯⎯⎯⎯⎯ Failed Tests 1 ⎯⎯⎯⎯⎯⎯
 Test Files  1 failed (1)
      Tests  1 failed | 4 passed (5)
```

**exit 1**；**单测 2** 转红，红在**「字段级错误出现」这半**（先跑的那条）——即校验**真的不再拦**。
⚠️ **与计划措辞的一处出入照实记**：计划写「期望：必填留空时 `quotaApi.create` 被调用」，而实测红在
**同一用例里更前面的那条正向断言**上（`msgAmountRequired` 不再出现），后面的 `not.toHaveBeenCalled()`
**没被执行到**。结论不变（该用例红、且红因是「校验被删」），但**红点位置与计划不同**。

**还原判据**：同 §A 的 `cp` 回写 + 哈希比对 → `dc09ea9566e9fde41e9610e57975e84fa1245ec4`。

---

## §C D3 —— 删掉 `actionRef.current?.reload()`

**破坏**：成功路径去掉表格重拉。

**期望**：单测 4 的**表格重拉**那半转红（`quotaApi.list` 不再被再调一次）。

**实际观测（2026-09-16 交付时实跑）**：

```bash
cd frontend && pnpm exec vitest run src/pages/quotas/QuotaListPage.form.test.tsx
```

```
→ expected 1 to be 2 // Object.is equality
⎯⎯⎯⎯⎯⎯ Failed Tests 1 ⎯⎯⎯⎯⎯
AssertionError: expected 1 to be 2 // Object.is equality
 Test Files  1 failed (1)
      Tests  1 failed | 4 passed (5)
```

**exit 1**；**单测 4** 转红，红在**表格重拉**那半（`quotaApi.list` 只被调了 1 次 = 挂载那次，没有第 2 次）。

**还原判据**：同 §A 的 `cp` 回写 + 哈希比对 → `dc09ea9566e9fde41e9610e57975e84fa1245ec4`。

---

## §D D4 —— 删掉 `setRefreshToken(...)`（**必须与 D3 分开做**）

**破坏**：成功路径去掉 KPI 重取的通路（`refreshToken` 不再自增）。

**期望**：单测 4 的 **KPI 重取**那半转红（`quotaApi.getSummary` 调用次数不再 +1），**而表格那半仍然绿** ——
这正是「两条刷新是两件事」的实证（`spec.md` §1.2 ③）。

**实际观测（2026-09-16 交付时实跑）**：

```bash
cd frontend && pnpm exec vitest run src/pages/quotas/QuotaListPage.form.test.tsx
```

```
→ expected 1 to be 2 // Object.is equality
⎯⎯⎯⎯⎯⎯ Failed Tests 1 ⎯⎯⎯⎯⎯
AssertionError: expected 1 to be 2 // Object.is equality
 ❯ src/pages/quotas/QuotaListPage.form.test.tsx:195:82
 Test Files  1 failed (1)
      Tests  1 failed | 4 passed (5)
```

**exit 1**。⚠️ **这一条的价值全在失败点的那一行号上**：D3 的失败文本与它**逐字相同**
（都是 `expected 1 to be 2`），只有**失败点不同**——D4 停在 **`:195`**（**KPI 重取**那条断言，
`getSummary` 只被调 1 次），而它**前面**的 `:191`（表格重拉）**通过了**。
⇒ **两条刷新是两条独立通路**，D3/D4 必须分开做这件事**在证据上是成立的**；
若把两条刷新合并成一条断言，D4 这个真缺陷（顶部三张汇总卡停在旧值上）会被表格刷新**完全掩盖**。

**还原判据**：同 §A 的 `cp` 回写 + 哈希比对 → `dc09ea9566e9fde41e9610e57975e84fa1245ec4`。

---

## §E D5 —— 把 `navigate('/quotas/create')` 写回按钮（**预期仍绿**）

**破坏**：工具栏「新建」按钮改回 `onClick={() => navigate('/quotas/create')}`。

**期望（如实预期）**：`typecheck` / `lint` / `build` **全绿** —— 因为路由是**字符串**，类型系统与打包器都不校验它指向哪。

**实际观测（2026-09-16 交付时实跑）**：

```bash
cd frontend && pnpm -s typecheck; pnpm -s lint; pnpm -s build
cd frontend && pnpm -s i18n:check; pnpm -s menu:check; pnpm -s perms:check; pnpm -s ui:check; pnpm -s zh:check
```

```
typecheck → exit 0
lint      → exit 0
build     → exit 0        （✓ built in 15.05s）
i18n:check → exit 0
menu:check → exit 0
perms:check → exit 0
ui:check   → exit 0
zh:check   → exit 0
```

**六道门禁 + 打包器全绿** —— 按钮指向一个**已被删除**的路由，**没有任何一道看着它**。

```bash
cd frontend && pnpm exec vitest run src/pages/quotas/QuotaListPage.form.test.tsx
```

```
→ Unable to find role="dialog"   （×5）
⎯⎯⎯⎯⎯⎯⎯ Failed Tests 5 ⎯⎯⎯⎯⎯
 Test Files  1 failed (1)
      Tests  5 failed (5)
```

**本项那 5 条行为层用例全红** —— 弹窗再也打不开。⚠️ **这不是「门禁变绿了」，是「行为层用例抓住了门禁抓不到的东西」**；
而这 5 条**不是门禁**（`pnpm lint` / `pnpm build` 都不跑它们）。

⚠️ **两次不成立的破坏，一并留痕**（它们本身就是「破坏跑全绿 ≠ 破坏没打中」的实例）：
1. **第一次（本会话前段）**：把 `openCreate` 整体换成 `navigate('/quotas/create')` 后，
   `setModalOpen` 成了**未使用符号** ⇒ `TS6133` ⇒ `typecheck` / `build` 红、`lint` exit 1。
   **那是「未使用符号」导致的红，不是「门禁看得见路由字符串」**——若把它当成「门禁有牙齿」，就是误读。
   改成**保留 `setModalOpen`**（`onCancel` 与成功路径仍在用它）后才拿到上面那张全绿的表。
2. **第二次（同一批，本文档记录的就是它的修正版）**：只把 `navigate(...)` **插到 `openCreate` 开头**、
   仍保留 `setModalOpen(true)` ⇒ **8 道门禁全绿、5 条单测也全绿**（弹窗照样开）。
   它给出的会是「看起来没红 ⇒ 没有空档」的**假结论**。⇒ 这条破坏**必须真的去掉「开弹窗」那一步**才成立。

⚠️ **这一条的结论是「空档」而不是「通过」**：登记进 `research.md` §5.3 与 `spec.md` §6，
**不得**因此声称「删除被门禁保证过」；替代判据是 `grep -rn "quotas/create" frontend/src frontend/e2e` **零命中** + 只读冒烟。

**还原判据**：同 §A 的 `cp` 回写 + 哈希比对 → `dc09ea9566e9fde41e9610e57975e84fa1245ec4`。

---

## §F D6 —— 只从 `en.ts` 删 `btnBack`

**破坏**：`en.ts` 删 `pages.quotaCreate.btnBack`，`zh-CN.ts` 保留。

**期望**：`check-i18n.mjs` 的**双向键集合比对**转红（2962 vs 2961）。

**实际观测（2026-09-16 交付时实跑）**：**原设计的破坏已不可执行**，改用同命名空间里的另一个键，理由如下。

**为什么换**：本项在提交 2 里**已经删掉**了 `pages.quotaCreate.btnBack`（弹窗里没有返回按钮 ⇒ 它是死键）。
到了交付阶段，`en.ts` 里**已经没有 `btnBack` 可删**，原破坏（「只从 `en.ts` 删它、`zh-CN.ts` 保留」）**无从做起**。
⇒ 改用**留在同一命名空间、且仍在被使用**的 `pages.quotaCreate.msgCreateFailed`（`QuotaListPage.tsx` 的失败分支在读它），
破坏形态与目的**完全相同**：**只从 `en.ts` 单侧删一个键**。

```bash
cd frontend && pnpm -s i18n:check
```

```
✗ en 缺少 1 个键（zh-CN 有）：
    pages.quotaCreate.msgCreateFailed

语言资源不一致。请在两个文件里补齐后重跑 npm run i18n:check。
```

**exit 1**，且**指名道姓**地报出缺的那个键 ⇒ 双语键集合比对**有牙齿**，`zh`/`en` 必须同批改（这正是 `research.md` §3 反例第二条的实证）。

**还原判据**：`cp` 备份回写后 `git hash-object frontend/src/i18n/en.ts` ==
`git rev-parse HEAD:frontend/src/i18n/en.ts` → **`22678e6b07cb1462cfd87c5147c4124c6ae75de7`**（内容级相等）。

---

## §G D7 —— 把 `FormModal` 换成裸 `<Modal>` 且不写 `width`

**破坏**：弹窗外壳改回裸 `<Modal>`（去掉 `width`）。

**期望**：`check-ui.mjs` 的 **R2** 转红 —— 证明「用共享原语」**不是风格偏好**，而是这条规则承认的合规路径
（`research.md` §2.1 的机制：`<FormModal` 不在 `<Modal` 的匹配内）。

**实际观测（2026-09-16 交付时实跑）**：

**破坏的准确形态**（四处一起改，否则红的会是别的东西，如实记）：
`<FormModal size="lg" … onSubmit={onCreate}>` → `<Modal … onOk={onCreate}>`（**去掉 `width`、去掉 `size`**）、
`</FormModal>` → `</Modal>`，并把 `FormModal` 从 `components/ui` 的 import 里摘掉、把 `Modal` 加进 `antd` 的 import。
⚠️ 不换 import 的话，`FormModal` 成了未使用符号 ⇒ 红的会是 `TS6133`（同 §E 的第 1 条陷阱），**不是 R2**。

```bash
cd frontend && pnpm -s ui:check
```

```
【R2 承载表单的 Modal 必须显式定宽】1 处
      第 228 行：<Modal
        onCancel={() => setModalOpen(false)
    修复：给 Modal 加 `width`，或改用 `@/components/ui` 的 `FormModal`（四档宽度 sm/md/lg/xl）。
```

**exit 1** ⇒ ① **R2 确实只认裸 `<Modal>`**（`<FormModal` 完全不在它的匹配里，`research.md` §2.1 的机制得到实证）；
② 失败信息**自带合规路径**（「或改用 `@/components/ui` 的 `FormModal`」）——所以「用共享原语」不是风格偏好，
而是这条规则**承认的**两条出路之一（另一条是显式写 `width`）。**候选池读数仍 55**（用原语时）这句的反面也在此得到印证。

**还原判据**：同 §A 的 `cp` 回写 + 哈希比对 → `dc09ea9566e9fde41e9610e57975e84fa1245ec4`。

---

## §H 门禁与覆盖率（**交付时实跑**）

```bash
cd frontend && pnpm typecheck && pnpm lint && pnpm i18n:check && pnpm menu:check \
  && pnpm perms:check && pnpm ui:check && pnpm zh:check && pnpm test:coverage && pnpm build
```

**逐道 exit code（2026-09-16 交付时实跑，全部在 D1–D7 **全部还原之后**跑）**：

```
typecheck=0   lint=0   i18n=0   menu=0   perms=0   ui=0   zh=0   build=0
```

**原样输出（只印不判/判定的关键行）**：

```
[i18n:check] ✓ 语言资源一致：zh-CN 2962 键 / en 2962 键；菜单路由与清单双向对齐（路由 58 条 / 清单 56 项，粗粒度别名 3 条）
[menu:check] ✓ 菜单清单是最新的（56 个菜单项，来源：RoleConstants.MENU_TREE）
[perms:check]✓ 权限判定接线校验通过（68 个权限码；8 个文件含已登记的 ADMIN 判断，共 9 处）
[ui:check]   扫描 271 个产品文件（其中 125 个 tsx）、303 个 Form.Item
             ✓ UI 规范校验通过（白名单内冻结的既存债 54 处，未新增违规）
[zh:check]   扫描 268 个产品文件（排除 src/i18n 与 *.test.*）、候选点 9150 个
             ✓ 源码硬编码中文校验通过（未登记命中 0 处；台账内冻结 266 处、4 条）
             口径外另有 55 处（全角标点 / 全角字母数字 9、带插值的模板串（字面部分） 46） —— **只印不判**
[build]      ✓ built in 15.27s
```

**逐条与 `quickstart.md` §1 的判据对照**：i18n 键 **2962 = 2962**（删 `btnBack` 后，与 §0 的预期一致）、
**路由仍 58 / 清单仍 56**（`research.md` §3 的「删那条路由不动任何机器计数」逐条落实）；
`menu:check` **56 不变**、`perms:check` **68 码 / 8 文件 9 处不变**、`ui:check` **冻结 54 未增长**、
`R2` 候选池**仍 55**（用共享原语 ⇒ 不进候选，§G 的 D7 给出了反面实证）；
`ui:check` **文件 272 → 271**、`Form.Item` **304 → 303**、`zh:check` **文件 269 → 268**
（三个都是**只印不判**的数，与 §0 的预期逐一吻合）；`zh:check` **命中 0 / 台账 266 处 4 条不变**
（`App.tsx` 的 55 不动）。
⚠️ **一处与立项期推算的差异已订正**（见 §0 的 ⚠️）：`Form.Item` 的实跑值是 **303**，
立项期推算的 `304 → 298` **是错的**——差在删掉那页有 **7** 个 `<Form.Item>` 开标签（6 字段 + 1 个包按钮的）。

**覆盖率（`pnpm -s test:coverage`，同一天在**同一棵未受破坏的树**上独立跑，exit **0**）**：

```
 Test Files  92 passed (92)
      Tests  469 passed (469)
   Duration  192.27s (transform 54.89s, setup 39.84s, collect 543.60s, tests 1870.30s, environment 65.48s, prepare 15.88s)

 % Coverage report from v8
-------------------|---------|----------|---------|---------|
File               | % Stmts | % Branch | % Funcs | % Lines |
-------------------|---------|----------|---------|---------|
All files          |   72.13 |    75.36 |   39.54 |   72.13 |
```

| 项 | 实测 | 阈值（**未改**） | 判据 |
|---|---|---|---|
| Statements | **72.13** | 33.6 | ✓ 高于 |
| Branches | **75.36** | 47.2 | ✓ 高于 |
| Functions | **39.54** | 21.4 | ✓ 高于 |
| Lines | **72.13** | 33.6 | ✓ 高于 |

**与阈值的比法是「高于」而不是「与上次的小数位比」**（`quickstart.md` §1）；四项**均高于**且
`vite.config.ts` 的 `thresholds` **一字未动**。`test:coverage` **exit 0**（若任一项低于阈值，该命令会非零退出）。

**计数对账**：测试文件 **92**（098 交付时 91 ⇒ **+1 即本项的 `QuotaListPage.form.test.tsx`**）、
用例 **469**（098 交付时 464 ⇒ **+5 即本项那 5 条**）——**增量与本项完全对得上**，
这正是「读数要有无第二写入者的工区才能归因」的判据（跑这一次时 `git status --porcelain` 只有本项工件）。
本项新增文件 `src/pages/quotas/QuotaListPage.form.test.tsx` 自身在报告里是
`✓ … (5 tests) 161911ms`；其被测对象 `QuotaListPage.tsx` 的覆盖率行是
`...aListPage.tsx | 100 | 77.77 | 80 | 100 | 87-120,192-193`（**Stmts / Lines 100**）。

**逐条还原后复跑**：八道门禁在还原之后**重跑了一遍**（上表即那一次），
两个被破坏过的文件按 `git hash-object` == `git rev-parse HEAD:<path>` 逐条核对：
`frontend/src/pages/quotas/QuotaListPage.tsx` → `dc09ea9566e9fde41e9610e57975e84fa1245ec4`、
`frontend/src/i18n/en.ts` → `22678e6b07cb1462cfd87c5147c4124c6ae75de7`（**内容级相等**，不称逐字节一致）。
`git status --porcelain` 只剩本项**有意未提交**的文档工件，**无一处破坏残留**。

**覆盖率 Branch 的 0.01 抖动**：**本次未出现**。098 交付时同一棵树两次读数差 0.01（75.26 / 75.27），
本项只跑了一次、读数是 **75.36**，**无第二个读数可比** ⇒ 按 098 的先例，这里**不单列一节**，
只记明「**本次只有一个读数，故 0.01 抖动这件事本次既未复现也未证伪**」。

### §H 附 1 只读冒烟

**步骤**：`http://localhost:5173/quotas` → 点工具栏按钮（⚠️ 见下）→ 看弹窗开、6 个字段与文案在、必填校验在、Esc 能关 → **不点提交**。

**观测（2026-09-16 交付时实跑，无头 Chromium）**：

| 步骤 | 实跑读数 |
|---|---|
| 页头标题 | **配额列表** |
| 工具栏按钮文案 | **创建配额**（⚠️ 计划里写的「新建」是简写，**不是**按钮上的字——见下） |
| 弹窗宽度 | **800px**（读 `.ant-modal` 的内联 `style`） |
| 弹窗标题 | **创建配额** |
| 6 个字段标签 | 年份 / 季度 / 团队 ID / 销售 ID / **配额金额（万元）** / 期间 —— **6/6 全在** |
| 年份默认值 | **2026**（`initialValues` 的当前年） |
| 弹窗主按钮 / 取消键 | **创建配额** / **取 消** |
| 必填留空点主按钮 | 「请输入配额金额」**出现**、「请选择期间」**出现**、「请输入年份」**不出现**（有 `initialValues`） |
| Esc | 弹窗**关闭**、URL 仍在 `/quotas` |
| 业务写请求 | **0 条**（全程唯一一次 POST 是登录的 `POST /api/v1/auth/login`） |

⚠️ **三处与本件/`quickstart.md` 立项期措辞不符的地方，照实记（`quickstart.md` §2 已同步订正、原文保留）**：
① 按钮文案是**「创建配额」**不是「新建」（`pages.quotaList.btnCreate`，改前改后都叫这个）；
② 金额标签是**「配额金额（万元）」**；③ 必填留空只有 **2** 条字段级错误（金额、期间），**年份不报错**。

⚠️ **这次冒烟是临时探针**（`e2e/` 下建、跑完即删，**不入库**，删后 `ls e2e/*.spec.ts | wc -l` 复核仍为 **7**）
⇒ 它**不是**可复跑的回归护栏，**不得**被引用成「有 e2e 看着弹窗」。
⚠️ **前置如实交代**：8081 的后端是**本会话自己起的**（`mvn -B spring-boot:run`，`curl /actuator/health` → **200**），
**不是**别的会话留下的；5173 复用既有的 Vite dev server。
写库冒烟**未做**（需用户明确同意）。

### §H 附 2 e2e

**实跑（2026-09-16）**：

```bash
cd frontend && pnpm exec playwright test e2e/module-page-auth.spec.ts
```

```
  13 passed (44.1s)
```

**exit 0**；清单里**已无** `'/quotas/create'`（提交 2 从 `FORM_PAGES` 摘掉）。⚠️ **两条边界**：
① 这是**导航型**用例，证明的是「这 13 个页面不是白屏、未被 401」，**不**证明创建弹窗的行为；
② **只跑了这一条**，`e2e/` 下另有 6 个 spec **未跑**（可能写共享开发库）⇒ **不得**声称「整套 e2e 全绿」。
⚠️ **不跑整套**（其余 spec 可能写共享开发库）；**不得**用门禁全绿冒充端到端证据。

---

## §I 订正不静默自查（**交付时实跑**）

**判据**：被订正的**旧值必须仍能被 grep 到**（零命中 = 静默改写）。逐条给出命令与命中数：

| # | 旧值 | 落点 | 期望 |
|---|---|---|---|
| 1 | `001–098` / `001~098` | `specs/roadmap.md`（**2 命中**：第 4、6 行 —— 099 立项条目下旧值降级为「上一条（原文保留）」）、`README.md:163`（**1 命中** —— 交付时 T007 才改，改时须保留旧值）；⚠️ `specs/README.md` **本就没有该串**（实测 0 命中，**属正常、不是静默改写**） | **合计非零** |
| 2 | `97 个功能模块` / `97（001–098，缺 069）` | `PROJECT_FEATURES.md`、`README.md:163` | **非零** |
| 3 | `'/quotas/create'` | `083/quickstart.md`（原文保留 + 带日期 ⚠️） | **非零** |
| 4 | `QuotaCreatePage` 的建页记述 | `PROJECT_FEATURES.md` 配额段 | **非零** |
| 5 | `2963` | `PROJECT_FEATURES.md` 的 i18n 行 | **非零**（原文保留 + ⚠️ 块） |

**实跑（2026-09-16 交付时）——五条各自的命中数**：

| # | 命令 | 命中 |
|---|---|---|
| 1 | `grep -c "001–098\|001~098" specs/roadmap.md README.md specs/README.md` | `specs/roadmap.md:2`、`README.md:1`、`specs/README.md:0` ⇒ **合计非零** ✓ |
| 2 | `grep -n "97 个功能模块\|97 个（001–098" PROJECT_FEATURES.md README.md` | `PROJECT_FEATURES.md:93`（留痕行）、`README.md:176`（⚠️ 块内的引号）⇒ **非零** ✓ |
| 3 | `grep -c "quotas/create" specs/083-engineering-consolidation/quickstart.md` | **2**（`:58` 原文 + `:60` 我加的 ⚠️ 块引用）⇒ **非零** ✓ |
| 4 | `grep -c "QuotaCreatePage" PROJECT_FEATURES.md` | **4** ⇒ **非零** ✓ |
| 5 | `grep -c "2963" PROJECT_FEATURES.md` | **3** ⇒ **非零** ✓ |

⚠️ **两条口径自证（「grep 模式的边界要自证」）**，都不影响上面结论，但照实记：
① **`specs/README.md` 的 0 命中属正常**——该文件**历来的** Spec 模块计数走的是模块表行数，**本就没有** `001–098` 这串；
它不是被本项静默改掉的（098 交付时那串也不在里面）。
② **`quickstart.md` §6 里列的第 2 条模式（`97（001–098`，**无空格**）与旧值写法不完全同形**：
旧值是 `97 个（001–098`（**有「个」**）。实测**无空格的那个模式命中在** `PROJECT_FEATURES.md:78`
（本项**新写的 ⚠️ 段里的表格行** `| Spec 模块 | 97（001–098） | …`），**有空格的那个模式命中在** `:93`（留痕行）。
⇒ **两种写法各命中一处、都是本项有意保留的旧值**，判据成立；但**命令写法本身不精确**这件事要记下来，
免得下一个人照抄 §6 那条命令、在别的仓库里得出「零命中 = 静默改写」的误判。
③ **交付后 `specs/README.md` 的 099 行**：本项把它的状态列由 `⏳` 改为 `✅`，
**并**在描述列末尾追加了带日期 ⚠️ 的交付段——**描述列的立项原文逐字未删**（`…不得声称删除被门禁保证过）`
那一整段仍在），符合「订正不静默：原文逐字保留 + 带日期 ⚠️」。

---

## §J 未验证边界（照实声明，**交付时复核每一条是否仍然成立**）

**交付时复核（2026-09-16）——逐条判「仍成立 / 已变化」**：

1. **没有门禁看着 `navigate()` 的目标是否存在** —— **仍成立**，且本次拿到了更强的证据：八道门禁 + `build`
   在「按钮指向已删路由」下**全 exit 0**（§E）。替代判据（grep 活引用零命中 + 只读冒烟）已实跑（§I、§H 附 1）。
   ⚠️ **但要说清新出现的一层**：本项那 **5 条行为层用例**在同一个破坏下**全红**（`Unable to find role="dialog"`）
   ⇒ 空档的准确边界是「**门禁**没有看着它」，**不是**「没有任何自动化看着它」。这 5 条**不是门禁**（`lint`/`build` 不跑它们）。
2. **`quota:*` 前端权限码缺失** —— **仍成立**（本项未动 `constants/permissions.ts`；`perms:check` 读数 68 码不变可为旁证）。
3. **4 个配额子页在 UI 里没有入口** —— **仍成立**（本项只改了列表页的工具栏，未加行操作列）。
4. **「文案逐字不变」在单测层不可证** —— **仍成立**（`setup.ts` 未动）。**新增一条**：本项**在浏览器里**看到了真实文案
   （§H 附 1 的 6 个标签、标题、按钮），但那是**一次性**的观测、**不是**常驻断言 —— 所以这句「不声称文案被断言过」
   **依然有效**，只是现在多了「曾经被人眼/无头浏览器核对过一次」这个事实。
5. **`QuotaCreatePage` 的删除本身无回归证据** —— **仍成立**（它从来没有过用例，`git diff` 里它只有删除）。
   本项**没有**为「删除」补任何用例，替代判据仍是 grep + 只读冒烟。
6. **写库冒烟未做** —— **仍成立**（共享开发库，需明确同意）。本次只读冒烟已确认**业务写请求 0 条**（§H 附 1）。

**另记三条本项在交付阶段新暴露、且不属于上面六条的边界**：
7. **`e2e/module-page-auth.spec.ts` 只跑了一条，其余 6 个 spec 未跑**（可能写共享库）⇒ 不得说「整套 e2e 全绿」（§H 附 2）。
8. **覆盖率只有一次读数** ⇒ 098 记过的「Branch 差 0.01」本次**既未复现也未证伪**（§H）。
9. **只读冒烟是一次性临时探针、已删除**，`e2e/` 下**没有**为创建弹窗留下任何常驻用例（§H 附 1）
   ⇒ 「弹窗能开」的常驻证据只有 §1.1 的那 5 条单测。
