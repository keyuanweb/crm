# 实现计划：中文串级残留清扫（098）

**依据**：[`spec.md`](./spec.md) · **日期**：2026-09-16 · **形制**：收口类（**不碰后端**）

---

## Context

**为什么是这一批**。`CRM_FEATURE_COMPARISON.md:375` 的 **P1 #16** 记着「页面级 i18n 已闭合，**11 个文件仍有中文字面量**
（最重 `LoginPage.tsx` 13 处），建议作日常清扫」。它是一条**从未被任何脚本支撑过的人工读数**（取于 2026-09-14），
而仓里**没有任何门禁看着「源码里的硬编码中文」**。本批做两件事：**把能清的清掉**，**给这件事装上第一道门禁**。

**前提已订正**（详见 `spec.md` §1.1）：用仓库里**既有**的 AST 扫描器 `frontend/.i18n-keys/find-hardcoded-zh.mjs` 实测，
**「11 个文件」复现不出来** —— 真实是 **300 处 / 15 文件**，且同一脚本在 2026-09-14 的提交上跑**逐文件逐数完全相同**（零漂移）。
「11」唯一算得准的构造是 15 − 4 个大户 = **11 文件 / 32 处**，**只能报告为推断**。

**用户裁决（2026-09-16）**：取「**窄口径清零 + 冻结台账**」与「**新脚本 `check-zh.mjs`**」。
「窄口径」= **用户可见且未走 `t()` 的** ⇒ 按门禁自己的口径清点落在 **34 处 / 12 文件**；余 **4 个文件 / 266 处**进**冻结台账**。

**预期结果**：P1 #16 从「11 个文件的悬空读数」变成**有门禁、有台账、有可复算命令**的闭合——
34 处清零，4 条台账理由写明「为什么这 266 处不该动」，并且**此后任何一处回潮都会让 `zh:check` 转红**。

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### 一、契约优先的 API 设计（不可协商）
- [x] **后端一行不改**：无端点、无 DTO、无迁移、无权限码。本批纯前端 + 一个 Node 门禁脚本 + 文档。
- [x] i18n 资源键**不是** OpenAPI 契约的一部分（`contracts/` 无涉）⇒ **不产出 `contracts/`**。

### 二、分层架构与关注点分离（不可协商）
- [x] 文案取词按既有分层各就各位：**组件/页面走 `useTranslation`**（`075` FR-P01 的既有裁决，逐字复述，不另立）；
  **非组件模块**（`apiClient.ts` / `visitService.ts`）**走 i18next 单例** `import i18n from '../i18n'` + `i18n.t(...)`。
  ⚠️ 该导入边在仓里**已存在**（`components/ErrorBoundary.tsx:5`、`pages/audit/AuditLogPage.tsx:3`），但**用途不同**
  （监听 `languageChanged`，非取词），且 `i18n.t(` 在全仓**零命中** ⇒ **本批是「服务层取词」的第一个先例，
  必须在 `check-zh.mjs` 文件头、`research.md`、第 5 次提交信息三处点名，不许静默引入。**
- [x] 门禁脚本落在 `frontend/scripts/`，**不进 `src/`** ⇒ 不参与覆盖率、不进产物。
- [x] **不在 `check-i18n.mjs` 里加规则**：它是早期形制（正则式、不剥注释、四道检查各自 `process.exit(1)`、失败不带行号、
  无白名单/无候选下限），在里面加这条要**同时重构它既有四道检查**，正是 `check-ui.mjs` R6 理由原文所警告的
  「混进别的批次会让两件事的回归都难以归因」。

### 三、数据完整性、安全与校验（不可协商）
- [x] **`zh:check` 必须有牙**：三分支双向校验 + `MIN_CANDIDATES`（反假绿）+ 逐条 `修复：`。四者缺一都会退化成「登记下来慢慢还」。
- [x] **台账是债务台账，不是批准清单**：每条 `reason` 必填散文，写明**类别**与**是否含欠账**；含欠账的必须**点名**。
- [x] **不改任何 zh 值**：新键的 zh 值与原字面量**逐字相同** —— 这是「界面与 e2e 不变」的唯一保证。
- [x] 无密码/密钥/网络/事务涉及。**不碰共享开发库、不碰 `.specify/feature.json`**。

### 四、测试优先与质量门禁（不可协商）
- [x] **新增门禁自身的用例**：`zh:check` 的定向破坏留痕（D1–D5）证明**每条分支都真的会红**，且**破坏逐条还原**。
- [x] 前端已有用例的改动面**实测只有 3 个文件 / 5 行**，全部是「断言中文 → 断言键名」的同一类改法。
- [x] **章程说「测试先于实现」（红→绿）**：本批遵守的方式是**定向破坏留痕**。⚠️ **如实说明边界**：
  本批实际编写顺序仍是**先实现、后补用例**（沿用 087/088/092/095/096/097 的既有做法），**不得**据此声称走过 spec-first；
  留痕证明的是**护栏有牙齿**，不是「红先出现」。
- [x] 测试金字塔：**不新增 e2e 用例**；e2e 只作为「界面未变」的**回归证据**跑一次。

### 五、简洁、可维护与可观测（不可协商）
- [x] **YAGNI**：不引入 i18next 插件、不改 i18n 初始化、不给 `t()` 包 hook。
- [x] **台账第一版登记全部 15 个文件 / 300 处**，让门禁**从落地第一刻就是绿的**，之后每个清零提交摘掉对应条目
  —— 这样「清零」是被机器的第三条分支强制的，不靠人记得。
- [x] **可观测**：`--list`（逐文件逐行清单）+ **信息性打印口径边界**。
- [x] **单关注点一脚本**，不合并、不重命名既有两个脚本。

**Gate 结论**：五项原则**无违规、无需 `Complexity Tracking`**。

---

## 已核实事实（实测，可直接采信）

**门禁口径**（= 既有脚本，逐字复用）：`ts.isStringLiteral` + `ts.isNoSubstitutionTemplateLiteral` + `ts.isJsxText`（`trim()` 非空）；
CJK `/[一-鿿]/`（**U+4E00–9FFF**）；跳过 `import`；排除 `src/i18n/**` 与 `*.test.*`。
**基线**：**300 处 / 15 文件**（`node .i18n-keys/find-hardcoded-zh.mjs` 尾行，本项立项前亲手复算）。

**清零集（34 处 / 12 文件）**：

| 处数 | 文件 | 逐行（口径 A 原样输出） |
|---|---|---|
| 13 | `pages/LoginPage.tsx` | L34–37 卖点四条、L122 登录失败兜底、L239/243/246 品牌区、L432 点击刷新验证码、L437 `alt` 验证码、L438 `aria-label` 验证码图片、L443 点击获取、L476 演示账号： |
| 3 | `components/SignSection.tsx` | L87 `alt="签名"`；L95 `t(…, { type: … ? '报价单' : '合同' })` 的**两个插值取值** |
| 3 | `pages/approval/ApprovalFlowPage.tsx` | L47/48/49 `{ value: 'ROLE', label: '角色' }` 一类**模块级常量表**三行 |
| 2 | `pages/approval/ApprovalCenterPage.tsx` | L251 审批详情、L289 `· 操作人 #` |
| 2 | `pages/opportunities/OpportunityListPage.tsx` | L226/229 `suffix="元"` |
| 2 | `pages/orders/OrderListPage.tsx` | L304 `emptyText`、L308 期次合计：¥ |
| 2 | `pages/tasks/TaskCalendarPage.tsx` | L78 `<Empty description="当日无任务" />`、L96 截止： |
| 2 | `services/visitService.ts` | L42/51 两个 `new Error(...)` —— `VisitListPage.tsx:148` 是 `message.warning((err as Error).message)`，**原样弹给用户** |
| 1 | `pages/search/SearchResultPage.tsx` | L45 `{ key: 'ALL', label: '全部' }` |
| 1 | `pages/stats/TeamLeaderboardPage.tsx` | L20 `t ? t('…') : '未设目标'` |
| 1 | `services/apiClient.ts` | L78 `extractErrorMessage(error, fallback = '请求失败，请稍后重试')` 的默认实参 |
| 2 | `App.tsx` | L645 `aria-label="折叠/展开菜单"`；L665 `label: '中文'`（**`zh-CN.ts:10` 与 `en.ts:10` 同值 `'中文'` ⇒ 键 `app.language.zh` 已存在、零新键**）；为对称把 L666 `'English'` 一并改走 `app.language.en` |

**冻结台账（4 条 / 266 处）**：

| 文件 | count | 理由（必填散文，须写明类别与是否含欠账） |
|---|---|---|
| `src/types/usageMap.ts` | 142 | **演示图谱数据**（节点/边的展示名），属 `075` FR-P03 排除的**动态数据** ⇒ 非 i18n 欠账。改它要动图谱演示逻辑，不在本项 |
| `src/constants/menuManifest.ts` | 67 | **生成物**（`gen-menu.mjs` 产出），`title` 是菜单**权威中文名**，`i18n/labelOf.ts#menuLabel()` 缺键时拿它降级 ⇒ **降级真源，非欠账**。手改会被下次 `menu:gen` 覆盖 |
| `src/App.tsx` | 55 | **路由 `name:` 元数据**：53 处只被 `check-i18n.mjs` 的路由枚举正则使用（界面渲染走 `MENU_MANIFEST` + `menuLabel()`）⇒ 非欠账；**另 2 处是真欠账并点名**：L417 `渠道 ROI`、L458 `工作流日志` —— 它们经 `SUB_PAGE_AFTER_MENU_KEY`（`App.tsx:163-166`）在 `:542` 被 `aliasRoute.name` 渲染。清零需给 route 加 `i18nKey` 并改 `:542` 的取词（**结构调整**），与那 53 处同处一段代码，留给下一批 |
| `src/components/breadcrumbTrail.ts` | 2 | **降级兜底中文名**（`SUB_PAGE_LABELS`），与 `labelOf.ts` 同一条降级机制 ⇒ 非欠账 |

**R6 联动（机器强制）**：`check-ui.mjs` 的 `R6_ALLOWED` 现有 **8 条 / count 合计 10**，其中 **2 条**的 `reason` 原文写着
「属 i18n 清扫批次」——`src/App.tsx`（`aria-label="折叠/展开菜单"`）与 `src/pages/LoginPage.tsx`（`aria-label="验证码图片"`）。
两者在本项都归零 ⇒ R6 的双向校验会强制这两条**必须摘除**，最终 **6 条 / count 合计 8**。
⚠️ **两本账别混**：R6 摘 2 条（`check-ui` 的账）与 `ZH_ALLOWED` 留 4 条（`check-zh` 的账）是**两件不同的事**。

**测试改动面**：`App.render.test.tsx:307,310`、`LoginPage.test.tsx:57-58`（断言改键名）；
`apiClient.test.ts:54` **视实测决定**（单例若已初始化则一行不改）。

---

## 结构决策

**产出 `research.md`，不产出 `data-model.md` 与 `contracts/`**：本批**无实体、无字段、无端点、无迁移**。
为凑齐工件而生成空壳文件正是「为了流程而流程」（与 092/095/096/097 的先例一致）。

**但 `research.md` 是本项的核心交付物之一**，它要装三件在别处装不下的东西：
① **两套口径的对照表**（AST 300/15 vs 正则+剥注释 373/37）与「11」的推断；
② **台账四条理由的逐条复核留痕**（白名单必须是债务台账 ⇒ 每条理由都要被独立证实过，不实就写不实）；
③ **服务层取词先例的立项说明**（`i18n.t(` 全仓零命中 ⇒ 这是第一个先例，理由、边界、
以及 `075` FR-P01/02 只裁到「页面」的空缺都要写明）。

**工件产出方式**：**手写**（照 `.specify/templates/` 与 086–097 已入库工件的体例），**不运行任何 `/speckit-*` 命令**，
**不碰 `.specify/feature.json`**（共享单槽指针、gitignored、从历史里恢复不了）。

**本项不碰后端**：`mvn verify` **不作为本项的门禁**；后端读数只在「若顺手跑了」时如实记录，**不声称**。

### 门禁脚本的三个设计要点（照 `check-ui.mjs` 的形制，不发明新形制）

1. **台账式结构**：保留 `{ hits, candidates }` 的返回形态、`ZH_ALLOWED`（`{ file, count, reason }`）、
   `MIN_CANDIDATES.ZH`（**被执行的反假绿下限**）与 `CANDIDATE_READINGS.ZH`（历史实测读数，**只用于区分「扫描器坏了」
   与「规则该退役」**）；失败按 `【kind】N 处` 分组 + `第 N 行：<text>` + `修复：<fix>`。
2. **三分支双向校验**：未登记命中 ⇒ 红；`count` 多了 ⇒ 红；`count` 少了（**含已归零**）⇒ 红。
   第三条就是本项「清零」被机器强制的机制 —— **不改台账就红**。
3. **口径边界自证（信息性，不影响退出码）**：除主口径外跑一遍**宽口径次级扫描**，
   打印「本脚本判 N；**口径外另有 M 处**（全角标点 / 带插值模板串）」。
   ⚠️ **这一行不是门禁**（不进 `problems`、不改退出码），**必须在留痕里写明它不判**。

---

## Project Structure

```text
specs/098-i18n-zh-residue/
├── spec.md · plan.md · research.md · quickstart.md · tasks.md
├── falsification-evidence.md
└── checklists/requirements.md

frontend/scripts/check-zh.mjs          # 【新】源码硬编码中文门禁（口径复用既有 AST 扫描器 + 台账 + 边界自证 + --list）
frontend/package.json                  # 改：加 "zh:check"
frontend/scripts/check-ui.mjs          # 改：R6_ALLOWED 摘 2 条 + 块头带日期 ⚠️ 订正（原文保留）
.github/workflows/ci.yml               # 改：加一步 zh:check（⚠️ 本仓无远端、CI 一次都不会触发，口径以本地为准）

frontend/src/i18n/{zh-CN,en}.ts        # 改：新增键 ≈ 30（zh 值 == 原字面量；en 值写真英文；同一次提交）
frontend/src/pages/LoginPage.tsx · components/SignSection.tsx · pages/approval/ApprovalFlowPage.tsx
frontend/src/pages/approval/ApprovalCenterPage.tsx · pages/opportunities/OpportunityListPage.tsx
frontend/src/pages/orders/OrderListPage.tsx · pages/tasks/TaskCalendarPage.tsx
frontend/src/pages/search/SearchResultPage.tsx · pages/stats/TeamLeaderboardPage.tsx
frontend/src/services/visitService.ts · services/apiClient.ts · App.tsx
frontend/src/App.render.test.tsx · pages/LoginPage.test.tsx · services/apiClient.test.ts（视实测）

PROJECT_FEATURES.md · specs/README.md · specs/roadmap.md · CRM_FEATURE_COMPARISON.md   # 登记与订正
```

---

## 分步与提交（7 次提交，各自可回退，**每次提交后门禁都必须是绿的**）

| # | 提交 | 内容 |
|---|---|---|
| 1 | `docs(098): 立项` | 本目录 6 件工件 + `specs/README.md` 模块表加 098 行（状态如实写「⏳ 进行中」）+ `specs/roadmap.md` 加 098 行（**勾选框留空、不预勾**）+ 编号说明纳入 098 |
| 2 | `feat(098): check-zh.mjs 门禁与冻结台账` | 新脚本 + `package.json` + `ci.yml`；**台账第一版登记全部 15 个文件 / 300 处**（= 现状）⇒ 门禁落地即绿，且证明台账**覆盖了现状**（未登记的命中一条都没有） |
| 3 | `refactor(098): LoginPage 清零 13 处` | `LoginPage.tsx` + i18n 键 + `LoginPage.test.tsx` 断言改键 + `ZH_ALLOWED` 摘 LoginPage 条目 + **`check-ui.mjs` 摘 R6 的 LoginPage 条目**（两条必须同批：清零一落地，两本账都会因「count 少了」转红） |
| 4 | `refactor(098): 十个页面/组件清零 19 处` | 除 LoginPage / 服务层 / App.tsx 之外的 10 个文件 + i18n 键 + 对应 `ZH_ALLOWED` 条目摘除 |
| 5 | `refactor(098): 服务层两处改走 i18next 单例` | `apiClient.ts` + `visitService.ts` + i18n 键 +（视实测）`apiClient.test.ts` + 台账摘除；**提交信息里点名这是「服务层取词」的第一个先例** |
| 6 | `refactor(098): App.tsx 两处清零并摘除 R6 剩余 i18n 白名单` | `App.tsx` + `App.render.test.tsx` + `ZH_ALLOWED` 的 App.tsx count 57→55 + **摘 R6 的 App.tsx 条目** + R6 块头带日期 ⚠️ 订正（`10/8` → `8/6`，原文逐字保留 + 更新两条 `awk` 复算命令） |
| 7 | `docs(098): 数字落点、P1 #16 订正、勾选与实测读数` | `PROJECT_FEATURES.md` + `CRM_FEATURE_COMPARISON.md` + `specs/README.md`/`roadmap.md` 交付态 + `tasks.md` 勾选与交付块 + `falsification-evidence.md` 实测读数 |

---

## 验证

### 门禁（每次提交前）

```bash
cd frontend && pnpm typecheck && pnpm lint && pnpm i18n:check && pnpm menu:check \
  && pnpm perms:check && pnpm ui:check && pnpm zh:check && pnpm test:coverage
```

- 七道既有门禁 + `zh:check` 逐条 exit 0；`ui:check` 的**冻结台账不得增长**（**以实跑读数为准**）、R6 自身应为 **8/6**。
- `i18n:check` 会打印键数 ⇒ **键数的三个落点必须一致**（实测、`PROJECT_FEATURES.md:19`、`specs/README.md` 的 098 行）。
- 覆盖率四项与 **33.6 / 47.2 / 21.4** 比，**不与上次的小数位比、不改阈值**；**工区须无第二个写入者**。
- **后端不跑**（无 Java 改动）。

### e2e（本项最重要的「界面未变」证据）

```bash
cd frontend && pnpm test:e2e     # 前提：8081 上已有后端在跑
```

**判据**：`frontend/e2e/` 下 444 处中文断言**一行不改**且全绿。
⚠️ 按仓规**不得擅自重启可能归并行会话所有的共享后端**；没有可用后端就**不跑**并**如实写明「未跑 e2e」**。

### 口径边界自证（必做，且必须写进留痕）

`zh:check` 会打印「本脚本判 N；口径外另有 M 处」。**独立复核一次 M**：用一条与主口径不同的取法
（`rg --pcre2` 宽区间 + 剥注释）量同一批节点范围，**两个读数必须相符**；不符就说明边界那行写错了。
留痕里要**写明这一行不判**。

### 定向破坏留痕（每条分支都要**被观测到转红**；逐条做、逐条还原；破坏期间不提交）

| # | 令其转红的方式 | 该红的判据 |
|---|---|---|
| D1 | 把清零过的一处**改回中文字面量**（回潮） | 第一分支：**未登记命中** ⇒ 红（**「防回潮」的核心**） |
| D2 | 某条台账 `count` **加 1** | 第二分支：count 多了 ⇒ 红 |
| D3 | 某条台账 `count` **减 1** | 第三分支：count 少了 ⇒ 红 |
| D4 | 文件已清零、条目**只改 `count` 不删** | 第三分支（**含已归零**）⇒ 红 ⇒ 证明「清零」是机器强制的 |
| D5 | 让扫描器退化（摘掉 `JsxText` 分支 / 改成只认单行） | `MIN_CANDIDATES.ZH` 自检失败 ⇒ 红（**反假绿有牙**） |
| D6 | **反向**：把 `App.tsx` 的 aria-label 改回裸字符串 | `check-ui` 的 **R6** ⇒ 红（证明 R6 摘除白名单后**仍看着它**，摘除不是关掉） |
| D7 | **反向**：给 `en.ts` 加一个 zh-CN 里没有的键 | `i18n:check` 双向对齐 ⇒ 红（证明双语必须同批） |

**还原判据**：前端文件因 `core.autocrlf` 在 checkout 时可能写成 CRLF ⇒ 用 `git cat-file -p HEAD:<path> | sha1sum`
判**内容级相等**，**不称「逐字节一致」**（照 097 §C(ii) 的既有订正）。

### 手工冒烟

**本项不需要**（纯前端文案替换 + 门禁脚本，无可手工观察的新行为）。**不声称**做过 ——
界面不变这件事由 e2e 的 444 处断言承担，比人工点两下强。

---

## 风险

| 风险 | 缓解 |
|---|---|
| **zh 值被写成「近义改写」** ⇒ 界面悄悄变了 | 铁律：zh 值**逐字复制**原字面量（含全角标点、前导空格、`：`）。e2e 一行不改且全绿是**唯一判据**；e2e 跑不了时以「`git diff` 逐条对账」代替，并如实标注它不是端到端证据 |
| **en 值敷衍** | `check-i18n.mjs` **只查键对齐与空串**，**没有断言看着翻译质量** ⇒ **必须如实写进留痕「本项无护栏看着它」**；例外：`app.language.zh` **本就同值**（endonym），是**故意不译** |
| 台账理由不实（把欠账洗成「不是缺陷」） | 四条理由**逐条独立复核**并留痕；不实就照实改 |
| **服务层取词是仓里第一个先例** | 三处点名；边界写明「只用于非组件模块拿不到 hook 这一种情形」；若 lint 或既有门禁报错，**回退到台账登记**并在留痕里写明 |
| R6 摘除后误以为「规则退役了」 | D6 做**反向破坏**证明 R6 仍看着；块头 ⚠️ 块写明「摘的是两条白名单，规则本身不动」 |
| 台账首版登记了「不会命中的条目」 | 第二/第三分支会当场红 ⇒ 首版必须逐条与实跑清单对齐；`--list` 输出即对账凭据 |
| 误把「11 个文件」的历史读数改掉 | **订正不静默**：P1 #16 原文逐字保留 + 带日期 ⚠️ 块；自查判据是「旧值仍能被 grep 到」 |
| 覆盖率/冻结台账沿用陈旧数字 | 一律**实跑取值**写进 `tasks.md` 交付块 |

## 明确不做

见 `spec.md` §4（8 条，每条附理由）。本处只强调**最容易后来者「顺手补上」的三条**：
① 不清零 `App.tsx` 的 53 处路由 `name:`（界面不走它们）；
② 不清零 `App.tsx` 的 2 处真欠账（要动路由表结构，**已在台账点名**）；
③ 不放宽门禁口径到全角标点与带插值模板串（是**另一个决定**，会让 300 与台账整体重算）。

## 提交纪律

`ListAgents` 确认无并行会话写同一批文件 → **逐路径 `git add`，禁用 `git add -A` / `git commit -a`**（本仓多会话共用工作区）
→ 提交信息末尾带 `Co-Authored-By: Claude Code <noreply@anthropic.com>`。若同伴工作被卷入，用 `git reset --soft` 重做，
**绝不修改或丢弃另一会话的未提交工作**。**不把提交自己的哈希写进它携带的文件**；**复选框绝不回填/预勾**。
