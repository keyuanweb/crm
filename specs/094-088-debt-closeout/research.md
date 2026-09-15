# Research：088 收口三笔账的实测依据（094）

> 全部结论都标了来源。**读数是读数、推演是推演**——本项不做渲染级几何实测（那属 092 的家族），
> 凡是「读 antd 源码得到的语义」一律标注为推演。

---

## §1 `span="filled"` 为什么做不到（① 的依据）

**实测（探针自验证，2026-09-15）**：探针文件 `frontend/src/tmp-probe-filled.tsx` 里同时放两样东西——
一句**故意的**类型错（`const deliberatelyWrong: number = 'not a number'`）与待验写法
（`span="filled"`），跑 `pnpm exec tsc --noEmit`：

```
src/tmp-probe-filled.tsx(5,7): error TS2322: Type 'string' is not assignable to type 'number'.   ← 故意的那句
src/tmp-probe-filled.tsx(11,36): error TS2322: Type 'string' is not assignable to type 'number'.  ← span="filled"
EXIT=2
```

**自验证的意义**：故意的那句先证明**这个文件真的被编译过**。此前第一版探针叫 `.tmp-probe-filled.tsx`，
`tsconfig` 的 `include: ["src"]` **不匹配点号开头的文件** ⇒ 它根本没被编译、`tsc` 退出码 0 ⇒
「`span="filled"` 通过类型检查」是个**假绿**。这条坑本仓有过记载，本次复现。

**类型定义出处**：`node_modules/antd/es/descriptions/Item.d.ts` 声明 `span?: number`，
而 `<Descriptions.Item>` 是 `Descriptions` 的 `Item` 成员（children 写法走这条）；
只有 `descriptions/index.d.ts` 的 `DescriptionsItemType.span?: number | 'filled' | {[key in Breakpoint]?: number}`
允许 `'filled'`，而它服务于 **`items` prop**。运行时的 `useItems.js` **两种都支持**
（`if (span === 'filled') return { ...restItem, filled: true }`）——**运行时支持、类型堵死**，这就是「做不到」的确切含义。

⇒ **三条真实路径**（本项**都不做**，归入「Descriptions 现代化」）：
1. 把这些区块改用 `items` prop（`span: 'filled'` 与逐档 `span` 都在类型内）；
2. 写一个类型化的薄包装（内部一处断言，调用点干净）——**不采用**：包装层会掩盖 antd 后续的类型变更；
3. 维持 `span={3}` 并接受 dev 告警（**即 088/T044 的现状**，也是本项 3 处改动采用的写法）。

---

## §2 5 处 → 3 处：存量违规的实测缩水（③ 的依据）

088 的 T044 遗留项 2 原报「Track B 之外同类写死仍有 **5** 处」。逐处复核：

| 落点 | 原文 | 判定 |
|---|---|---|
| `components/SignSection.tsx` | `column={2}` + 全宽项 `span={2}` | **真违规** ⇒ 本项改 |
| `components/SurveyBlock.tsx` | `column={2}` + 全宽项 `span={2}` | **真违规** ⇒ 本项改 |
| `pages/portal/CustomerPortalPage.tsx` 服务状态块 | `column={3}` + 提交时间 `span={2}` | **真违规** ⇒ 本项改 |
| `pages/personal/PersonalCenterPage.tsx` ×2 | `column={{ xs: 1, sm: 2 }}` | **已合规**：`md` 及以上由 `DEFAULT_COLUMN_MAP` 补成 **3** ⇒ 是响应式的，不是写死 2 |
| `pages/portal/CustomerPortalPage.tsx` 查询结果面板 | `column={1}` | **不是违规**：刻意的单列结果面板；`1` 已是最窄档，机制上不可能因窄屏溢出 |

**口径**：原报 5 处是**按「字形相似」列的清单**（凡 `column={数字}` 都列进去了），
本次是**按「是否构成窄屏缺陷」判的**。差在判据，不在代码——故这一步是**订正清单**，不是「两处逃掉了」。

---

## §3 R8 的候选池解剖（FR-094-004 的依据）

`frontend/src` 下产品 tsx（排除 `*.test.tsx` 与 `src/test/`），2026-09-15 实测：

| 量 | 值 | 取法 |
|---|---|---|
| `<Descriptions>` 开标签总数 | **14** | 单行 10 + **跨行 4**（`^ *<Descriptions$`） |
| 其中 `column={{ … }}`（断点对象） | 10 | `grep -rc "column={{"` 逐文件合计 |
| 其中 `column={数字}`（写死） | **4** | 见下 |
| 写死里是**多列**（>1）的 | **3** | 本项要改的 3 处 |
| 写死里是 `column={1}` 的 | **1** | 例外（见 §2） |
| 用 `items=` 写法的 | **0** | 全部走 children 写法 |

**⚠️ 一次自证过的口径错误（记下来，因为它会重犯）**：第一次数「开标签总数」用的是
`grep -o "<Descriptions[ >]"`，得 **10**——**跨行的 4 个开标签全部漏掉**（`<Descriptions` 后面直接是换行，
不匹配 `[ >]`），而它与「`column={{` 也是 10」这个读数**恰好相等**，于是看起来自洽、不会自曝。
换成 `^ *<Descriptions$` 与 `^ *<Descriptions ` 两条分开数才得到 14。
⇒ 本项 R8 用**逐字符扫描器**（`scanTagEvents`，按标签名整体匹配、不依赖单行），但**下限的注释里写明了这一档失效的长相**。

**为什么下限取 12**：今天 14。余量 2 处留给合法的「删掉一个 `Descriptions` 区块」；
同时 **12 > 10**，能抓住上面那一档失效（只认单行开标签 ⇒ 掉到 10 ⇒ 红）。

---

## §4 零候选失败信息为什么是指错方向的（② 的依据）

`check-ui.mjs` 的零候选分支（反假绿机制）的 `fix` 文案只写了**一种**成因：
「请先确认该规则的正则/扫描器没有失效、扫描范围没有跑偏」。而两种成因的处置**相反**：

| 成因 | 长相 | 处置 |
|---|---|---|
| ① 扫描器失效 | 正则改坏、路径变了、只认单行标签 | **修扫描器**，候选点应回到实测值 |
| ② 规则已无可判对象 | 全库真的再没有该规则的判据对象 | **退役该规则**（连同 `MIN_CANDIDATES` 条目与白名单一起删）——先例：R2（T040）与 R3（T045）**逐条毕业**；更早的 `--strict` 两档机制也因「不再改变任何行为」而整条删除 |

**R3 的实测解剖**（今天仍是绿的，因为候选 1 > 下限 1）：

| 量 | 值 |
|---|---|
| 全库 `<Col` | **88** |
| 其中位于 `<Form>` 区域内（= R3 的候选点） | **1**（`pages/tags/TagListPage.tsx`，且它带 `flex`、无 `span`，本就合规） |
| `<FormGrid` 用法 | **58** |

⇒ R3 的候选池是被 **FormGrid 取代殆尽**的，它离「无可判对象」只差那**一个** `Col` 被重构掉。
那一刻红色会打印「怀疑扫描器」——**指错方向**。这就是②要改的全部内容：
**不改数值**（改数值才是放行），只让信息分得清。

### §4.1 `CANDIDATE_READINGS` 的实测值（**读数**，2026-09-15）

失败信息要能拿「历史读数」比对，所以这份表必须是**读数**、不能是估计。取法：给主循环加一行
`if (process.env.DUMP_CANDIDATES) …`（**临时补丁，用完立即逐字节还原，`sha256sum -c` 核对 OK**），
`DUMP_CANDIDATES=1 node scripts/check-ui.mjs` 一次跑全：

| 规则 | 候选点 | 命中 | 说明 |
|---|---|---|---|
| R1 | **33** | 33 | 品牌色字面量（含豁免路径里的真源） |
| R4 | **148** | 5 | 带 `required` 的 `Form.Item` |
| R5 | **149** | 6 | 同口径再含"有 label 但无 required"的那些 |
| R6 | **10** | 10 | **裸写法**的 placeholder / aria-label（走 `t()` 的不计入） |
| R7 | **21** | 0 | `components/*.tsx` 文件数 |
| R2 | **55** | 0 | 承载表单的 Modal |
| R3 | **1** | 0 | 见 §4：FormGrid 已把对象取代殆尽 |
| R8 | **14** | **0** | `<Descriptions>` 开标签（本项改完 3 处后，写死 >1 的数字**已归零**） |

**⚠️ 记下这次的过程**：上表最初是我**按印象填的估计**（R4 189 / R5 192 / R6 27 / R7 41 / R2 21），
跑一次实测后**五条里四条差得离谱**（R2 实际 55、是我估的两倍半；R6 实际 10、不到估计的四成）。
估计与读数在这一栏里**外观完全一样**——这正是本仓 `registry-checkmark-is-not-evidence-of-implementation`
那条教训的同型：**能对拍的数字必须真的量过**。（R3 的 1 与 R8 的 14 是先前量过的，故与实测相符。）

R8 的 **命中 0** 是本项 ③ 的收官读数：三处改完后，全库再无「`column` 写死为 > 1 的数字」。

---

## §5 三处改动各自带来什么（FR-094-002 的依据，**含实测**）

### 5.1 引擎语义（读源码）

`antd/es/descriptions/hooks/useRow.js` 的 `getCalcRows`：逐格 `count += span || 1`；
`count >= mergedColumn` 时 flush 本行（`count` 归零）；`count > mergedColumn` 时记 `exceed`
（dev 告警，`useRow.js:68`）并把该格 `span: restSpan = mergedColumn - count`（**截到剩余列数**，count 取加之前的值）。
行在 `count >= mergedColumn` 时即 flush ⇒ **每项开始时必有 `count < mergedColumn` ⇒ `restSpan ≥ 1`**，
不会出现 `span: 0` 的退化。

`Row.js` 的渲染口径**两种模式不同**（读源码，会同一次探针读数核对过）：
**bordered** 走 `component: ['th','td']` ⇒ 标签格 `colspan=1`、内容格 `colspan = span*2-1`；
**非 bordered** 走 `component: 'td'` ⇒ 单格 `colspan = span`。比较格位必须按各自口径读，不能混。

### 5.2 实测（探针，untracked、用完即删；jsdom ⇒ **只有 3 列档**可量）

⚠️ 两条边界：jsdom **没有布局引擎**，读到的是 **DOM 结构量 `colspan`**、**不是几何**；
`src/test/setup.ts` 把 `matchMedia` 桩成恒 false ⇒ `matchScreen` 返回 `undefined` ⇒ `mergedColumn = … ?? 3`
⇒ **只量到 3 列档**。xs/sm 两档见 5.3 的推演。

**① 合成块矩阵（同一次运行内对拍，形态照两个落点的项序）**

| 用例 | 每行格位（`colspan`，bordered：标签格/内容格） | dev 告警 |
|---|---|---|
| sign 形态，`column={2}` + `span={2}`（**旧**） | `[1,1,1,1 \| 1,3]` | **0** |
| sign 形态，`column={2}` + `span={3}`（只改 span） | `[1,1,1,1 \| 1,3]` — **与上一行逐格相同** | **2** |
| sign 形态，`column={3}` + `span={2}`（只改 column） | `[1,1,1,1,1,1]` — **两行并一行** | 2 |
| sign 形态，`column={{…}}` + `span={3}`（**新**，落在 3 档） | `[1,1,1,1,1,1]` — **与上一行逐格相同** | 2 |
| portal 形态，`column={3}` + `span={2}`（**旧**，落在 3 档） | `[1,1,1,1,1,1 \| 1,1,1,3]` | **0** |
| portal 形态，`column={{…}}` + `span={3}`（**新**，落在 3 档） | `[1,1,1,1,1,1 \| 1,1,1,3]` — **与上一行逐格相同** | **2** |

**② 真实组件对拍（非 bordered ⇒ `colspan = span`）**

| 落点 | 旧态（`column={2}` + `span={2}`） | 新态（`column={{…}}` + `span={3}`） |
|---|---|---|
| `SignSection` | `warn=0`，格位 `[1,1 \| 2]` | `warn=2`，格位 `[1,1,1]` |
| `SurveyBlock` | `warn=0`，格位 `[1,1 \| 2]` | `warn=2`，格位 `[1,1,1]` |

（告警数是**每次渲染 2 条**——同一个 `Descriptions` 在挂载与数据到达后各渲染一次。T044 记的「8 条 / 4 文件」是同一口径下的累计。）

### 5.3 结论：**两件事必须分开说**（这是本节的核心）

- **`span` 由 `{2}` 改 `{3}`：中性**。合成矩阵里「只改 span」与「旧」逐格相同；「只改 column」与「新」逐格相同。
  代价是 **dev 告警由 0 变 2**（每次渲染）——因为 `span` 声明值超过了该档列数，antd 把它截回来并告警。
  **这不是本项引入的新形态**：T044 已让 Track A/B 的 4 个页面如此（`plan.md` 与 `tasks.md` 都记了）。
- **`column` 改写：这才是可见变化**，逐档如下（3 档为实测，1/2 档为推演）：
  | 落点 | xs（1 档） | sm（2 档） | md 及以上（3 档） |
  |---|---|---|---|
  | `SignSection` / `SurveyBlock`（旧 `column={2}` = **每档都是 2 列**） | 旧：2 列（窄屏被压成两窄列，**正是缺陷**）→ 新：**1 列** | 旧=新：两单格一行 + 全宽项满行 | 旧：2 列（两单格一行 + 全宽项满行）→ 新：**3 列**（三格一行，全宽项占 1/3） |
  | portal 服务状态（旧 `column={3}` = 每档 3 列） | 旧：3 列（缺陷）→ 新：**1 列** | 旧：`[a,b,c \| d,末项]` → 新：**`[a,b \| c,d \| 末项]`** | **旧=新**（逐格相同，见 5.2 最后两行） |

  ⇒ **订正我在本项初稿里的一处错**：我原写「列数变化只发生在 sm/xs，md 及以上列数不变」——**错的**。
  那只对 portal 成立；`SignSection`/`SurveyBlock` 的旧写法是 `column={2}`，**md 及以上原本就是 2 列**，
  本项把它变成 3 列。这一处**必须**计入可见变化（SC-094-006 的欠账），不能读成"只动窄屏"。

**为什么仍要按 FR-015 改成 `span={3}`**：字母要求「全宽项的 `span` 与该 `column` 的上限一致」，
T044 已在 4 个页面上这么做（Track A/B 逐字同形）。本项让其余 3 处**同形**——这是 088 用户故事 2
（「同一件事在每个页面上长一样」）的落点，而实测表明这次同形的代价**只有告警**（格位不变）。
它的根治要等「Descriptions 现代化」（改用 `items` API 才能表达逐档 `span`），本项不做。

**两处本项自己踩的坑，记在这里以免后人重犯**：
1. 探针第一版**过滤器写错**（`'Sum of column span'`，真串带反引号 `` `span` ``）⇒ 量到 `warn=0`，
   与推演冲突才发现。**读数为 0 时先自证过滤器**。
2. 「md 及以上不变」是**没量就写**的推断，被探针推翻（见上）。
