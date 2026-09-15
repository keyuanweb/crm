# 证伪证据（094）

本文件记的是**读数**：探针与定向破坏的**逐字输出**、以及每次临时改动的逐字节还原核对。
结论部分见 `research.md`；本文件只回答"你凭什么这么说"。

## 0 口径与边界（先立规矩，免得读数被误读）

| 项 | 值 | 含义 |
|---|---|---|
| 环境 | jsdom（vitest） | **没有布局引擎** ⇒ 量到的是 **DOM 结构量 `colspan`**，**不是几何量（像素宽高）** |
| `matchMedia` | `src/test/setup.ts` 恒桩成 `matches: false` | ⇒ antd 的 `matchScreen` 返回 `undefined` ⇒ `mergedColumn = … ?? 3` ⇒ **只量到 3 列档** |
| 因而 | xs(1) / sm(2) 两档**仍是推演** | 只在 `research.md` §5.3 的表格里出现，本文件不声称量过 |
| 临时改动的纪律 | 逐个做、逐个逐字节还原 | 每次还原后 `sha256sum -c` 核对，输出见各节 |

**参与本次取证的文件（改动后的最终 sha256，还原核对用的就是这些值）**：

```
ff18128a3924bd248c9f608358ae65459be35b4fc20a7f7e96bd3690e34bb6f1  src/components/SignSection.tsx
2fc21c98bd24b030052643689bba3c1881932d48a3cd07c6165057c9da0bd016  src/components/SurveyBlock.tsx
27d972e1a2fdf1e6c3437e7edba83ce6a23f1ccf214050c73f20422dc1db1f41  src/pages/portal/CustomerPortalPage.tsx
```

（`check-ui.mjs` 未列入：它的最终内容里**含并行会话 093 的一处 hunk**，本项只提交自己的部分，见 §5.3。）

---

## 1 FR-094-002：三处改动各自带来什么（A/B 对拍）

**探针**：`frontend/src/tmp-probe-colspan.test.tsx`（untracked、**用完即删、不进仓库**）。
它把 `console.error` 换成一个桶，**只筛含 `Sum of column` 的条目**计数，并把 `tr`/`td,th` 的 `colspan` 逐格 dump 出来
（**按行**还原，才看得出"哪几格同处一行"）。

### 1.1 合成块矩阵（**同一次运行内**对拍，形态照两个落点的项序）

`bordered` 模式：每项渲染成**两个格**（标签 `th` + 内容 `td`），内容格 `colspan = span*2-1`。

```
PROBE|sign|col2|span2（**旧**）|warn=0|rows=[1,1,1,1 | 1,3]|cells=["TH1:a","TD1:v","TH1:b","TD1:v","TH1:LONG","TD3:long"]
PROBE|sign|col2|span3（只改 span）|warn=2|rows=[1,1,1,1 | 1,3]|cells=["TH1:a","TD1:v","TH1:b","TD1:v","TH1:LONG","TD3:long"]
PROBE|sign|col3|span2（只改 column）|warn=2|rows=[1,1,1,1,1,1]|cells=["TH1:a","TD1:v","TH1:b","TD1:v","TH1:LONG","TD1:long"]
PROBE|sign|colObj|span3（**新**，jsdom=3 档）|warn=2|rows=[1,1,1,1,1,1]|cells=["TH1:a","TD1:v","TH1:b","TD1:v","TH1:LONG","TD1:long"]
PROBE|portal|col3|span2（**旧**，jsdom=3 档）|warn=0|rows=[1,1,1,1,1,1 | 1,1,1,3]|cells=["TH1:a","TD1:v","TH1:b","TD1:v","TH1:c","TD1:v","TH1:d","TD1:v","TH1:LONG","TD3:long"]
PROBE|portal|colObj|span3（**新**，jsdom=3 档）|warn=2|rows=[1,1,1,1,1,1 | 1,1,1,3]|cells=["TH1:a","TD1:v","TH1:b","TD1:v","TH1:c","TD1:v","TH1:d","TD1:v","TH1:LONG","TD3:long"]
```

**逐对读**：

| 对照 | 结论 |
|---|---|
| 第 1 行 vs 第 2 行（**只改 `span`**） | 格位**逐格相同**、行边界相同；告警 **0 → 2** ⇒ **`span` 改写自身是中性的** |
| 第 3 行 vs 第 4 行（**只改 `column`**） | 格位**逐格相同** ⇒ 新写法的 3 档观感 = 把 `column` 从 2 改成 3 之后的样子 |
| 第 1 行 vs 第 3 行 | `[1,1,1,1 \| 1,3]` → `[1,1,1,1,1,1]` ⇒ **两行并成一行**，这才是可见变化 |
| 第 5 行 vs 第 6 行（portal） | 格位**逐格相同**、**告警同为 0 → 2**、行边界相同 ⇒ portal 在 3 档**观感不变** |

### 1.2 真实组件（非 bordered ⇒ 一个格，`colspan = span`）

**旧态是临时把两个生产文件改回旧写法取得的**（改法：`column={{ xs: 1, sm: 2, md: 3 }}` → `column={2}`、
`span={3}` → `span={2}`；两份文件各改这两处），**用完立即还原并核对 sha256**：

```
PROBE|SignSection（真实组件）|warn=0|rows=[1,1 | 2]|cells=["TD1:pages.sign","TD1:pages.sign","TD2:pages.sign"]
PROBE|SurveyBlock（真实组件）|warn=0|rows=[1,1 | 2]|cells=["TD1:pages.surv","TD1:pages.surv","TD2:pages.surv"]
```

还原后的新态（同一探针、同一次运行）：

```
PROBE|SignSection（真实组件）|warn=2|rows=[1,1,1]|cells=["TD1:pages.sign","TD1:pages.sign","TD1:pages.sign"]
PROBE|SurveyBlock（真实组件）|warn=2|rows=[1,1,1]|cells=["TD1:pages.surv","TD1:pages.surv","TD1:pages.surv"]
```

⇒ **旧态在 3 档是 2 列（`[1,1 | 2]`），新态是 3 列（`[1,1,1]`）**。这与 §1.1 第 1→3 行的推演一致，
也是**独立于合成块**的第二个来源。

**还原核对（逐字节）**：

```
src/components/SignSection.tsx: OK
src/components/SurveyBlock.tsx: OK
```

### 1.3 结论（与 `research.md` §5.3 同）

- `span` 由 `{2}` 改 `{3}`：**格位中性**，代价是 dev 告警 **0 → 2**（每次渲染；同一个 `Descriptions` 在挂载与数据到达后各渲染一次）。
- `column` 改写：**可见变化**。`SignSection`/`SurveyBlock` 旧写 `column={2}` ⇒ **每档都是 2 列**，
  故 xs 2→1、**md 及以上 2→3**（**这一处在桌面宽度下肉眼可见**）；portal 旧写 `column={3}` ⇒ xs 3→1、sm 3→2、**md 不变**。
- ⚠️ **这条结论订正了本项初稿的说法**：初稿写「列数变化只发生在 sm/xs，md 及以上列数不变」——**只对 portal 成立**，
  对 `SignSection`/`SurveyBlock` 是**错的**（它们旧写法就不是 3 列）。订正已同步进 `spec.md` 的 FR-094-002 / SC-094-006 与 `plan.md`。

---

## 2 定向破坏①：R8 真的会红（SC-094-004 / FR-094-005）

**手法**：把 `src/components/SignSection.tsx` 的 `column={{ xs: 1, sm: 2, md: 3 }}` **写回 `column={2}`**
（模拟"有人又写死回去"），跑 `node scripts/check-ui.mjs`。

```
EXIT=1

✗ UI 规范校验失败：1 处问题

【R8 `Descriptions` 的 `column` 不得写死为大于 1 的数字】1 处
  src/components/SignSection.tsx
      第 79 行：<Descriptions column={2} size="small">
    修复：改成断点对象 `column={{ xs: 1, sm: 2, md: 3 }}`；**全宽项**（备注/签名/长文本这类整行字段）的 `span`与该 `column` 的上限一致（`span={3}`）。若这里**就是要单列**，写 `column={1}`——那是最窄档，本规则不判它。
```

**读作**：`exit 1`；**只红这一处**（隔离干净）；红在 **R8** 上、指到了**正确的文件与行**、并给出了**可粘贴的修法**。

**还原后**：

```
src/components/SignSection.tsx: OK
EXIT=0
✓ UI 规范校验通过（白名单内冻结的既存债 54 处，未新增违规）
```

⚠️ **关于那个 54**：HEAD 时是 **53**；工区多出来的 1 处是**并行会话 093** 给
`R1_ALLOWED` 的 `DashboardPage.tsx` 加的（`count: 7 → 8`，配合他们未提交的 `DashboardPage.tsx`）。
**本项提交里不含这一处**（只提交自己的 hunk），故**在本项的那个提交上读数仍是 53**——
与 `spec.md` 的 SC-094-002（「该数不变」）一致。详见 §5.3。

---

## 3 定向破坏②：候选归零时，失败信息分得清两种成因（SC-094-003）

**手法**：把 `rule8()` 里的 `scanTagEvents(code, 'Descriptions')` 改成 `'DescriptionsBROKEN'`——
**模拟成因①「扫描器失效」**（标签名写错/正则改坏这一类），候选点因此归零。

```
EXIT=1
扫描 268 个产品文件（其中 124 个 tsx）、297 个 Form.Item

✗ UI 规范校验失败：1 处问题

【R8 自检失败】1 处
  -
      `Descriptions` 的 `column` 不得写死为大于 1 的数字
      本规则只解析出 0 个候选点（下限 12；2026-09-15 的历史实测读数 14）。
    修复：护栏在输入为空时通过等于没有护栏。但先分清是**哪一种**失败——两种成因的处置相反：
    ① **扫描器失效**（正则改坏、扫描范围跑偏、只看单行标签…）：修扫描器，候选点应回到上面那个历史读数。
    ② **规则已无可判对象**（如实测读数本就很小或已归零，例如 R3 只剩 1 个候选点、FormGrid 已取代它）：
       那这条规则**已经不再是一条护栏**，应按先例（R2 于 T040、R3 于 T045 逐条毕业；`--strict` 两档机制
       也因"不再改变任何行为"而整条删除）**退役它**——连同 MIN_CANDIDATES 条目、CANDIDATE_READINGS 条目
       与它的白名单一起删，**不是**把下限调低来放行。
    判据：把该规则的候选点用另一条独立的取法量一遍（`grep`/探针）。与扫描器读数一致 ⇒ 是 ②。
```

**读作**：两种成因**都在**、处置**各自给出**、并**印出了历史实测读数（14）**供比对——
这正是②要的全部内容（**不改任何数值**，只让信息分得清）。
**改造前**的文案只写「请先确认该规则的正则/扫描器没有失效」，即**只覆盖成因①**，会把成因②指错方向。

**还原后**：`src/components/SignSection.tsx: OK`（同批核对）、`node scripts/check-ui.mjs` ⇒ `EXIT=0`。

---

## 4 本项自己踩的坑（逐条自证，供后人避雷）

1. **探针的过滤器写错 ⇒ 量成 0**：第一版筛的是 `'Sum of column span'`，而 antd 的真串是
   `Sum of column \`span\` in a line …`（**带反引号**）⇒ 一条都筛不到，得到"告警 0 条"，
   与"`span={2}`→`{3}` 会新增告警"的推演**冲突**才发现。改成筛 `'Sum of column'` 后读数才正常。
   ⇒ 与 `grep-pattern-boundaries-must-self-verify` 同型：**读数为 0 时，先自证取数口径**。
2. **推演当成结论**：「md 及以上列数不变」是**没量就写**的推断，被 §1.2 的读数推翻（旧态在 3 档就是 2 列）。
   ⇒ 已改写 `research.md` §5.3、`spec.md`（FR-094-002 / SC-094-006）、`plan.md`（风险表）。
3. **估计值当成实测值**：`check-ui.mjs` 的 `CANDIDATE_READINGS` 初版是我**按印象填的**
   （R4 189 / R5 192 / R6 27 / R7 21 / R2 21）。实测后**五条里四条差得远**（R2 实际 **55**、是我估的两倍半；
   R6 实际 **10**、不到估计的四成）。⇒ 已全部换成实测值，并把这一步记进 `research.md` §4.1。
   **这一栏里估计与读数外观完全一样**——所以必须真的量。

---

## 5 未做 / 存疑（不静默）

1. **`CustomerPortalPage` 的服务状态块未渲染真实组件**：它是个大页面、依赖多，本次没为它搭渲染脚手架；
   portal 侧的读数来自**同形合成块**（§1.1 第 5、6 行，项序照抄该块）。
   ⇒ 该块的**行为**有合成块作证，**"真实组件渲染出来确实如此"没有**（它是 bordered，合成块也是 bordered，故口径一致）。
2. **xs(1) / sm(2) 两档全是推演**：jsdom 只到 3 档（§0）。要量这两档得走 092 那条真实布局引擎的路。
3. **并行会话 093 在同一文件里留了一处 hunk**：`check-ui.mjs` 的 `R1_ALLOWED` 里
   `src/pages/stats/DashboardPage.tsx` 的 `count: 7 → 8`（带注释「093 复测」），
   它与其未提交的 `DashboardPage.tsx` 配套。**本项不提交它**（提交它会让那个提交孤立地红：登记 8、实际 7），
   也不改动它。⇒ 本项提交后，工区里 `check-ui.mjs` 相对索引**仍显示为 modified**，那是**对的**。
4. **同两个登记文件上，093 的登记与我的登记撞在**同一行**（补记，T019 时发现）**：`specs/README.md` 的
   版本行、`specs/roadmap.md` 的 `**最后更新**` 行各自被两个会话往同一行追加条款 ⇒ 整行是一处 hunk、
   **按行切不开**。处置：按字面量锚点重建「只含我的段」的版本 + **往返断言**（把对方的段塞回去后
   逐字节等于工作区），再用 `git hash-object -w --path=` → `update-index --cacheinfo` 入索引，
   **工作区一字未动**。⇒ 本项的两个提交里 `093` 的登记命中 **0 处**。

---

## 6 SC-094-004 的静态读数（可复算，且口径自证）

**口径**：只扫产品源码（排除 `*.test.tsx` 与 `src/test/`），标签名边界取
「`<Descriptions` 之后是**非标识符字符**或**行尾**」——与 `check-ui.mjs` 的
`scanTagEvents(code,'Descriptions')`（整标签名匹配）同界，故不会把 `<Descriptions.Item` 算进来。

```
$ cd frontend
$ grep -rn --include='*.tsx' -E '<Descriptions([^A-Za-z0-9._-]|$)' src | grep -v '\.test\.tsx:' | grep -v '^src/test/' | wc -l
14
$ ...同上... | grep -E 'column=\{[0-9]+\}'
src/pages/portal/CustomerPortalPage.tsx:147:        <Descriptions column={1} bordered size="small">
$ ...同上... | grep -E 'column=\{[0-9]+\}' | grep -v 'column={1}' | wc -l
0
```

⇒ 开标签 **14**、写死**多列** **0**、`column={1}` **1 处**（在册例外，见 FR-094-003），与 SC-094-004 一致。

**⚠️ 口径自证（这一步不能省）**：把边界换成更窄的「`<Descriptions` 之后是**空白或 `>`**」，读数掉到 **10**
——按行的 `grep` 里 `[[:space:]]` **匹配不到换行**，而有 **4 个详情页**把 `<Descriptions` 写在**行尾**、
属性换到下一行：

```
src/pages/contracts/ContractDetailPage.tsx:313:        <Descriptions
src/pages/customers/CustomerDetailPage.tsx:353:          <Descriptions
src/pages/leads/LeadDetailPage.tsx:213:        <Descriptions
src/pages/orders/OrderDetailPage.tsx:295:        <Descriptions
```

**为什么这条自证值得留档**：10 恰好落在 `MIN_CANDIDATES.R8 = 12` **之下**——也就是说，
「只认单行开标签的扫描器」这一档失效的**实测读数就是 10**，会被下限拦下并打印两种成因。
此前「12 是水位」是**推演**（见 FR-094-004 的注释），这里给出了它的**实测对照值**；
同时它也是一个现成的例子：**读数从 14 变 10 时，先怀疑口径、别先怀疑代码**（本轮我自己就差点
把 10 当成"代码变了"）。
