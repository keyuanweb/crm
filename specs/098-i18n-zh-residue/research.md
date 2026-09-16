# 调研：中文串级残留清扫（098）

**日期**：2026-09-16 · **配套**：[`spec.md`](./spec.md) · [`plan.md`](./plan.md)
**本件装三件在别处装不下的东西**：① 两套口径的对照与「11」的推断；② 台账四条理由的**逐条复核**；
③ **服务层取词先例**的立项说明。**外加一条对本项立项期选项数字的订正**（§1.4，必须读）。

---

## §1 口径

### §1.1 两套口径的对照（都以**可一键复算**为准）

| | **口径 A（本项门禁采用）** | **口径 B（宽区间正则）** |
|---|---|---|
| 取法 | TypeScript 编译器 API，数 AST 节点 | `rg --pcre2` 宽区间 + 逐行匹配 |
| 数的东西 | `StringLiteral` + `NoSubstitutionTemplateLiteral` + `JsxText`（`trim()` 非空） | **行**（含注释、含模板串、含正则字面量） |
| CJK 范围 | `/[一-鿿]/` = **U+4E00–U+9FFF** | 加 `U+3000–303F`（全角标点）、`U+FF00–FFEF`（全角字母数字） |
| 排除 | `src/i18n/**`、`*.test.*` | 同左 |
| **实测读数** | **300 处 / 15 文件** | **2452 行 / 194 文件**（其中**行首即注释**的 **1876 行**，占 **76.5%**） |
| 复算命令 | `cd frontend && node .i18n-keys/find-hardcoded-zh.mjs`（尾行） | 见 §1.3 的 `rg` 一行式 |

**结论：口径 B 有 3/4 以上是注释行** ⇒ 它**不能当门禁**，否则「违规数」里塞满注释。
这正是口径 A 用编译器 API 的全部理由，也是既有脚本文件头写的原话：

> 为什么要用编译器 API 而不是 grep：本仓库的注释按约定就是中文，grep `一-鿿` 会把满屏注释都算进来，
> 噪音大到无法判断。这里用 TypeScript 解析出 AST，**注释天然不在 AST 里**。

⚠️ **旁证（不入判据）**：调研期另用一条**剥注释的 Python 分类器**（按**处**统计、非按行）测得过
「非注释 **373 处 / 37 文件**；注释 6032 处」。**该分类脚本没有入库** ⇒ 按本仓的「可复现性」纪律，
它**只能作旁证**，不作为任何判据，也不写进 SC。本项的一切数字**以口径 A 为准**。

### §1.2 「11 个文件」为什么复现不出来

逐文件对账（同一套 AST 计数，在 `8a7c438` = 2026-09-14 最后一次提交的树上 vs 今天）：

```
  142 -> 142   src/types/usageMap.ts          67 ->  67   src/constants/menuManifest.ts
   57 ->  57   src/App.tsx                    13 ->  13   src/pages/LoginPage.tsx
    3 ->   3   src/components/SignSection.tsx  3 ->   3   src/pages/approval/ApprovalFlowPage.tsx
    2 ->   2   src/components/breadcrumbTrail.ts       2 ->   2   src/pages/approval/ApprovalCenterPage.tsx
    2 ->   2   src/pages/opportunities/OpportunityListPage.tsx   2 -> 2  src/pages/orders/OrderListPage.tsx
    2 ->   2   src/pages/tasks/TaskCalendarPage.tsx    2 ->   2   src/services/visitService.ts
    1 ->   1   src/pages/search/SearchResultPage.tsx    1 ->   1   src/pages/stats/TeamLeaderboardPage.tsx
    1 ->   1   src/services/apiClient.ts
TOTAL 09-14: 300 / 15      TOTAL now: 300 / 15
```

**逐数零漂移** ⇒ 「11」**不是漂移造成的**，它来自**另一套口径**。唯一能**算术上**得到 11 的构造是
从 15 里减掉 4 个「文档已承认其正当性」的大户（`usageMap` 142 + `menuManifest` 67 + `App.tsx` 57 + `breadcrumbTrail` 2）：
`15 − 4 = 11 个文件`、`300 − 268 = 32 处`。

⚠️ **这条减法只能报告为「推断」，不能报告为「已验证」**：文档里**没有留下**生成「11」的命令或输出，
P1 #16 也没给处数。另两个自然口径的实际值也都不是 11（**用户可见 UI 文本含全角标点 = 13 文件**、**非注释中文 = 37 文件**）。

### §1.3 可复算命令

```bash
# 口径 A（唯一权威；尾行即「合计：300 处硬编码中文，分布在 15 个文件」）
cd frontend && node .i18n-keys/find-hardcoded-zh.mjs

# 逐行清单（只看非大户的 32 处；注意脚本输出的路径分隔符是反斜杠，故用 ^src. 匹配）
node .i18n-keys/find-hardcoded-zh.mjs | awk '/^src./ { keep = ($0 !~ /usageMap|menuManifest|App.tsx|breadcrumbTrail/) } keep'

# 口径 B（宽区间正则，按行；含注释 —— 只用于对照，不作为判据）
rg -n --pcre2 '[\x{4e00}-\x{9fff}\x{3000}-\x{303f}\x{ff00}-\x{ffef}]' src \
  --glob '*.ts' --glob '*.tsx' --glob '!**/*.test.*' --glob '!**/*.spec.*' \
  --glob '!**/__tests__/**' --glob '!src/test/**' --glob '!**/*.d.ts' --glob '!src/i18n/**' -c \
  | awk -F: '{s+=$2; n++} END {print s, n}'      # ⇒ 2452 194

# 口径 B 里「行首即注释」的那部分（下界：不含行尾注释）
rg -n --pcre2 '[\x{4e00}-\x{9fff}\x{3000}-\x{303f}\x{ff00}-\x{ffef}]' src \
  --glob '*.ts' --glob '*.tsx' --glob '!**/*.test.*' --glob '!**/*.spec.*' \
  --glob '!**/__tests__/**' --glob '!src/test/**' --glob '!**/*.d.ts' --glob '!src/i18n/**' \
  | rg -c '^\s*[^:]+:[0-9]+:\s*(//|\*|/\*)'      # ⇒ 1876
```

### §1.4 ⚠️ **订正：立项期选项里给的「约 37 处 / 13 文件」与门禁口径差 3 处 / 1 文件**

**用户裁决时看到的选项**写的是「用户可见且未走 `t()` 的约 **37 处 / 13 文件**」。那个数取自 §1.1 的**旁证口径**
（剥注释分类器，按处统计）。而本项门禁用的是**口径 A（AST）**，两者对同一批「用户可见」处的读数差在**三类字面量**上：

| 差在哪 | 例子 | 口径 A 能否看见 |
|---|---|---|
| **全角标点**（U+FF1A 等，在 U+4E00–9FFF 之外） | `DepartmentDetail.tsx:43` 的 `{label}：`、`UserManagementPage.tsx:116` 的缩进 `'　'` | **看不见** |
| **带插值的模板串**（`TemplateExpression`，非 `NoSubstitutionTemplateLiteral`） | `RecycleBinPage.tsx:34` 的 `` `${t('…')} ${n} 条` `` | **看不见** |
| **正则字面量**（`RegularExpressionLiteral`） | `UsageMapPage.tsx:114,115,120` 的 `/驳回|失败|…/` | **看不见** |

⇒ **本项口径下的窄口径清零集是 34 处 / 12 文件**（32 处 + `App.tsx` 的 2 处），**不是 37/13**。
**差的那部分不在本项射程**，已写进 `spec.md` §4 非目标 5 与 §6 未验证边界（**口径外两类只打印不判**）。
**记在此处是为了「订正不静默」** —— 立项期的选项数字与门禁口径不一致这件事，**必须留下痕迹**，
不能让读者以为「本项声称清了 37 处」。

---

## §2 清零集（34 处 / 12 文件）的逐行清单与分类

三类，**每类一种改法**：

| 类 | 处数 | 文件 | 改法 |
|---|---|---|---|
| **(a) 组件/页面文案** | 30 | `LoginPage`(13)、`SignSection`(3)、`ApprovalCenterPage`(2)、`OpportunityListPage`(2)、`OrderListPage`(2)、`TaskCalendarPage`(2)、`SearchResultPage`(1)、`TeamLeaderboardPage`(1)、`App.tsx`(2)、`ApprovalFlowPage`(3)※ | `useTranslation` + `t()`（`075` FR-P01） |
| **(b) 模块级常量表** | 3 | `ApprovalFlowPage.tsx:47-49` 的 `{ value: 'ROLE', label: '角色' }` 一类 | **表里改存键**、组件内 `t()` —— 范式见 `src/constants/enumLabels.ts`（值是英文键、中文只在注释） |
| **(c) 服务层文案** | 3 | `visitService.ts:42,51`、`apiClient.ts:78` | **i18next 单例**（先例，见 §4） |

※ `ApprovalFlowPage` 的 3 处既是 (a) 的渲染面、也是 (b) 的形态，归 (b) 的改法。

**为什么 (c) 确属「用户可见」**（曾是一处开放点，已定论）：`VisitListPage.tsx:148` 是
`message.warning((err as Error).message)` —— 服务层抛出的中文**原样弹给用户**。

---

## §3 冻结台账（4 条 / 266 处）理由的**逐条复核**

> 台账是**债务台账**，不是批准清单。以下每条都**独立核过**，证据是**文件里已有的东西**或**可复算的命令**。

### §3.1 `src/types/usageMap.ts`（142）——✅ 理由成立，且**该文件头早已写明**

复核结果**比预期强**：该文件**头部注释第 3 行起**就写着这件事，本台账只是把它**提升为可执行判据**：

> **本文件里的中文字符串是刻意保留的，不是漏翻。** 这里存的是流程图节点标题、节点说明、角色说明这类**产品文案**
> （约 140 条），由产品侧维护；一期 1.4 的双语化覆盖的是按钮、表头、提示等**界面骨架文案**。
> 这部分若由工程侧自行翻译，会与产品口径分叉，而且流程图上的文案译错比不译更难被发现。
> ……`UsageMapPage` 在英文界面下是**刻意混排**的……**这是已知状态，不是遗漏。**
> **将来替换时注意**：页面现在是直接渲染 `flow.title` / `node.title` / `action.label` 的（未经过 `t()`），
> 所以改成 i18n 键**需要同时改页面**，不能只改本文件。

⇒ **非 i18n 欠账**（属 `075` FR-P03 排除的动态数据 / 产品文案）。实测 142 处与文件头自述的「约 140 条」相符
（差额来自同一条文案的 `title` 与 `description` 分列）。**替代时机是「等英文文案由产品提供」**，不是工程侧决定。

### §3.2 `src/constants/menuManifest.ts`（67）——✅ 生成物，已实证

`scripts/gen-menu.mjs:29` = `const OUT = resolve(here, '../src/constants/menuManifest.ts')`，
`:280` = `writeFileSync(OUT, source)`；生成器头注释 `:11` 写「生成物进版本库（可评审、可 diff）」。
`title` 是菜单**权威中文名**，`i18n/labelOf.ts#menuLabel()` 在缺键时拿它降级。
⇒ **非欠账**（降级真源）；**手改会被下次 `pnpm menu:gen` 覆盖**。

### §3.3 `src/App.tsx`（55）——✅ 成立，且**含 2 处真欠账（已点名）**

55 = **53 处路由 `name:` 元数据** + **2 处真欠账**。53 处的性质：界面渲染走 `MENU_MANIFEST` + `menuLabel()`，
route 上的 `name:` **只被 `check-i18n.mjs` 的路由枚举正则**（`/path:\s*'([^']+)',\s*name:\s*/`）使用 ⇒ **非欠账**。

**2 处真欠账**（实测确认渲染路径）：`L417 渠道 ROI` 与 `L458 工作流日志` —— 它们经
`SUB_PAGE_AFTER_MENU_KEY`（`App.tsx:163-166`，只有这两条）在 `App.tsx:542`
`children.push({ … label: aliasRoute.name })` 被渲染 ⇒ **界面真的显示这两条中文**。

**修法已有现成范式（这是本条复核最有价值的产出）**：同一个仓里、**同样这两条路径**，
`components/breadcrumbTrail.ts:64-67` 已经这么写：

```ts
const SUB_PAGE_LABELS: Record<string, TrailNamedSegment> = {
  '/marketing/roi': { i18nKey: 'channelRoi', title: '渠道 ROI' },      // 缺键时降级为 title
  '/workflows/logs': { i18nKey: 'workflowLogs', title: '工作流日志' },
}
```

⇒ `App.tsx` 的路由表**缺的正是 `i18nKey` 这个字段**。但加它要动 route 的**类型**与 `:542` 的取词，
与那 53 处同处一段代码 ⇒ **留给下一批**（`spec.md` §4 非目标 2）。

### §3.4 `src/components/breadcrumbTrail.ts`（2）——✅ 降级兜底，**非欠账**

同上 §3.3 引的 `SUB_PAGE_LABELS`：`i18nKey` 在场（取词优先走键），注释原文写「**缺键时降级为这里的中文名**」，
与 `i18n/labelOf.ts#menuLabel()` 是**同一条降级机制** ⇒ **非欠账**。

### §3.5 复核的反面结论（**如实记录**）

- 台账里**没有**任何一条是「因为难改所以不动」。四条各有**独立可核的真源**（文件头 / 生成器 / 正则消费方 / 降级机制）。
- 唯一被点名为**真欠账**的是 `App.tsx` 的 2 处，且**给了修法与范式**。
- ⚠️ **本项没有复核过**这两条欠账的**视觉影响**（e2e 不覆盖这两个子页面的菜单标签）—— 记在此，**不声称**。

---

## §4 服务层取词先例（本项的第二个核心产出）

**事实**：`i18n.t(` 在 `frontend/src` 下**零命中**；`import i18n from '../i18n'` 只有两处
（`components/ErrorBoundary.tsx:5`、`pages/audit/AuditLogPage.tsx:3`），且**用途都是监听 `languageChanged`**，不是取词。

**决策文档怎么说的**（先查决策文档，避免把实现偏差洗成「文档问题」）：

- `specs/075-page-i18n/spec.md` **FR-P01**：「所有**页面**必须使用 `useTranslation` hook」；
  **FR-P02**：「所有**用户可见的静态文本**必须使用 `t()` 函数调用」；**FR-P03**：动态数据保持原样。
- `specs/074-full-i18n/spec.md` 的 FR-I18N-02..05 同旨，另排除路由路径与**测试文件中的中文**。

⇒ **两份裁决都只裁到「页面」**，**没有裁过服务层**。而 `visitService` 抛出的文案**确实用户可见**（§2 已实证）。
**这个空缺由本项补上，并且必须点名它**（不许静默引入一个新写法）。

**本项的裁决与边界**：

1. **非组件模块**（拿不到 hook）走 **i18next 单例** `import i18n from '../i18n'` + `i18n.t(...)` —— 这是 i18next 的标准用法；
   `src/i18n/index.ts` 是独立模块（既非组件也非 hook），从服务层导入它**不构成跨层依赖**（与 logger 同类）。
2. **边界**：**只用于「非组件模块拿不到 hook」这一种情形**。组件/页面**仍走 `useTranslation`**（`075` FR-P01 不被动摇）。
3. **若门禁或 lint 因此报错 ⇒ 回退到台账登记**，并在 `falsification-evidence.md` 里写明（**不硬推**）。
4. `extractErrorMessage` 的默认实参改为 `i18n.t('…')` —— 默认实参**在调用时求值**，故语言切换后取到的是**当前语言**，语义正确。

**⚠️ 一个未定的分支由实测定**：`src/services/apiClient.test.ts:54` 断言 `toBe('请求失败，请稍后重试')`。
`apiClient.ts` 走的是**单例**，而 `src/test/setup.ts` 的 `vi.mock('react-i18next')` **盖不住单例**
⇒ 若单例在测试环境已初始化（`src/i18n/index.ts` 在导入时即 `init`），该断言**取到真实 zh 值、一行不改**；
否则改成键名。**先单跑该文件取读数，再决定**（`spec.md` FR-022）。

---

## §5 已知空档（**如实登记，本项不改**）

| # | 空档 | 为什么不改 |
|---|---|---|
| 1 | **`en.ts` 的翻译质量没有任何门禁看着** —— `check-i18n.mjs` 只查**键双向对齐**与**空串** | 要判「译得好不好」得引入人审或外部服务；本项只能**按实写好英文**并在留痕里写明**这一条无断言看着** |
| 2 | **口径外的两类**（全角标点、带插值模板串）本项**只打印不判** | 放宽口径会让 300 这个基线与台账**整体重算**，是另一个决定（`spec.md` §4 非目标 5） |
| 3 | **`en.ts` 里 18 处中文**（全在注释里）与 `zh-CN.ts` 的注释中文 | `075` FR-P04 已排除「代码注释中的中文」；且注释不在 AST 里，门禁看不见 |
| 4 | **`check-i18n.mjs` 是早期形制**（正则式、不剥注释、四道检查各自 `process.exit(1)`、失败不带行号、无白名单/无候选下限） | 改造它要**同时重构既有四道检查**，会让两件事的回归难以归因（`check-ui.mjs` R6 理由的原话） |
| 5 | **`R6_ALLOWED` 里 `ProductListPage.tsx` 的 `Price (CNY)`** | 已判为**语义问题不是 i18n 问题**（reason 原文写「要拍板的业务文案」），不属本项 |
| 6 | **`App.tsx` 的 2 处真欠账** | 修法见 §3.3（加 `i18nKey`），属结构调整，与 53 处元数据同段代码 |

---

### §5.1 ⚠️ **实做订正（2026-09-16，写脚本时实测）：`MIN_CANDIDATES` 抓不住「漏了 `JsxText` 分支」**

立项时（`plan.md` 验证表 D5）预设的破坏是「摘掉 `JsxText` 分支 ⇒ `MIN_CANDIDATES` 自检失败」。
**写脚本时把候选池的构成量清了，这个预设不成立**：

| 候选点构成 | 实测（2026-09-16） |
|---|---|
| 字符串字面量 + 无插值模板串 | **9075** |
| 非空 `JsxText` | **86** |
| **合计** | **9161** |

摘掉 `JsxText` 分支只让候选点从 9161 掉到 9075 —— 要抓住它，下限得卡在 **9076~9161** 这个
**0.9% 的窗口**里，而**任何一次正常的文案删除都会误报**。**那种下限不是护栏，是噪音源。**

⇒ **处置（两处都改了，不硬凑）**：

1. `MIN_CANDIDATES.ZH` 取 **8000**（实测 − 13%），它守的是**大面积失效**：
   `walk` 没扫到文件、TS 解析全失败、`visit` 提前 return、只看单行……（**已冒烟观测过**：
   让 `walk` 扫 0 个文件 ⇒ `【ZH 自检失败】`、exit 1、还原后 sha1 与破坏前相等）。
2. **「漏掉 `JsxText`」改由第三分支兜住**：漏掉它会让 CJK 命中数掉下来，而台账登记的是 300 ⇒
   **「count 少了」当场红**。**这是被换了一条护栏，不是被放弃** —— 计数变了必然触发双向校验。

**方法论**：候选池的**构成**要量，不能只看**总量**。总量 9161 看着"离下限很远、很安全"，
但其中 99.1% 来自另一个分支 ⇒ 那条下限**对本次要防的那个破坏完全不敏感**。
这与本仓既有的那条教训同源：**判据要锚在它真正管的那个量上**。

### §5.2 口径外 55 处的构成（实测）

`zh:check` 打印的口径外读数 = **55 处**，构成：

| 类别 | 处数 | 例 |
|---|---|---|
| 全角标点 / 全角字母数字 | **9** | `stats/DashboardPage.tsx:1089` 的 `：`、`users/UserManagementPage.tsx:116` 的全角空格 `　` |
| 带插值的模板串（字面部分） | **46** | `recycle/RecycleBinPage.tsx:34` 的 `条`、`tickets/TicketDetailPage.tsx:265` 的 `用户#` |

⇒ 这与 `research.md` §1.4 里「37/13 与 34/12 差 3 处 / 1 文件」的**方向一致**，
但**不是同一个数**：§1.4 说的是「用户可见且未走 `t()`」的差，本节说的是**全仓所有节点**的差（两者口径不同，**不许互换**）。

---

## §6 本项与既有护栏的关系（不推翻、不重复）

- **`check-i18n.mjs` 不动**：它管**资源键**（双向对齐、空值、菜单双射、枚举标签键），本项管**源码字面量**。
  两者关注点不同、**不重叠、不合并**（`plan.md` 结构决策）。
- **`check-ui.mjs` 的 R6 只摘 2 条白名单**（`spec.md` §3.4）：R6 管「**裸写法**的 `placeholder` / `aria-label`」，
  本项管「**中文字面量**」；**两本账别混**。R6 的规则本身、`MIN_CANDIDATES.R6`（1）与 `CANDIDATE_READINGS.R6`（10）**都不动**；
  ⚠️ `CANDIDATE_READINGS.R6 = 10` 是**当前裸写法命中数**，**不是台账大小**（摘完 2 条后它仍是 10 —— 它数的是命中，不是条目）。
- **`RequirePermissionCatalogTest` 那条「反向刻意不断言」的裁决**与本项无关（那是后端权限码面）。
- **`eslint` 不设防硬编码中文**（无 i18n 插件，devDependencies 里也没有）—— 本项**不新增 eslint 插件**（YAGNI），
  门禁由 `check-zh.mjs` 承担，与既有 `check-*.mjs` 同一形制。
