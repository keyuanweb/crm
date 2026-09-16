# 任务：中文串级残留清扫（098）

**Created**: 2026-09-16
**图例**：`[ ]` 未完成 / `[x]` 已完成。**立项阶段一律不预勾。**
**⚠️ 本项的实际编写顺序是「先实现、后补用例」**（沿用 087/088/092/095/096/097 的既有做法）——
**不得**据此声称走过 spec-first；§H 的定向破坏留痕证明的是**护栏有牙齿**，不是「红先出现」。
**⚠️ 本项的活动半径**：**纯前端 + 一个 Node 门禁脚本 + 文档**。
**零后端改动、零迁移、零权限码、无 `contracts/`**（i18n 资源键不是 OpenAPI 契约的一部分）。

**⚠️ 清零与台账是一对**：本项清掉 **34 处 / 12 文件**，把它们从台账里**摘掉**；
余下 **4 条 / 266 处**留在台账里，每条**带理由**。**台账只许减不许增**。

---

## 阶段 A 工件与登记（提交 1）

- [x] T001 写 `spec.md` / `plan.md` / `research.md` / `quickstart.md` / `falsification-evidence.md` / `tasks.md` / `checklists/requirements.md`
- [x] T002 `research.md` 必须装齐三件：① 两套口径对照表 + 「11」的推断；② **台账四条理由的逐条复核**；③ **服务层取词先例**的立项说明；**外加盟立期选项数字的订正**（37/13 → 34/12）
- [x] T003 `specs/README.md` 模块表加 098 行（**状态列如实写「⏳ 进行中」**，**不预勾**）
- [x] T004 `specs/README.md` 的**编号说明**纳入 098（**按文本锚定位，不按行号**——行号会漂），并**如实写明 `092`~`097` 未在该段逐项登记**（该段记录的是编号异常与需点明的形制，**不是全量索引**）
- [x] T005 `specs/roadmap.md` 的 `## 当前进度` 加 098 行，**勾选框留空、不预勾**（照 095/096/097 先例）

## 阶段 B 门禁与冻结台账（提交 2 —— 本项的结构性交付）

- [x] T006 新建 `frontend/scripts/check-zh.mjs`：**逐字复用**既有 AST 扫描器的口径（`ts.isStringLiteral` + `ts.isNoSubstitutionTemplateLiteral` + `ts.isJsxText`（`trim()` 非空）、CJK `/[一-鿿]/`、跳过 `import`、排除 `src/i18n/**` 与 `*.test.*`）
- [x] T007 照 `check-ui.mjs` 的形制组织：`ruleDefs` 式结构、`{ hits, candidates }` 返回形态、`ZH_ALLOWED = [{ file, count, reason }]`（**reason 必填散文**）
- [x] T008 台账理由必须写明**类别**（生成物 / 路由元数据 / 演示数据 / 降级兜底）**与其中是否含欠账**；含欠账的**必须点名**（`App.tsx` 条目点名 2 处）
- [x] T009 **台账第一版登记全部 15 个文件 / 300 处**（= 现状）⇒ 门禁**落地即绿**，同时证明台账**覆盖了现状**（未登记的命中一条都没有）
- [x] T010 **三分支双向校验**：未登记命中 → 红；某条 `count` **多了** → 红；某条 `count` **少了（含已归零）** → 红
- [x] T011 `MIN_CANDIDATES.ZH`（**被执行的反假绿下限**）与 `CANDIDATE_READINGS.ZH`（只用于区分「扫描器坏了」与「规则该退役」）
- [x] T012 **口径边界自证**（信息性）：同一批 AST 节点跑一遍放宽口径（全角标点 + 全角字母数字 + 收 `TemplateExpression`），打印「本脚本判 N；口径外另有 M 处」；**不进 `problems`、不改退出码**
- [x] T013 `--list` 开关：逐文件逐行清单（= 本批工作单，也是下一批的入口）
- [x] T014 失败汇报按 `【kind】N 处` 分组 + `第 N 行：<text>` + **`修复：<fix>`**
- [x] T015 **文件头点名「服务层取词」是仓里第一个先例**（`i18n.t(` 全仓零命中），并写明边界：**只用于「非组件模块拿不到 hook」这一种情形**
- [x] T016 `frontend/package.json` 加 `"zh:check": "node scripts/check-zh.mjs"`
- [x] T017 `.github/workflows/ci.yml` 加一步 `zh:check`（与既有步骤并列）
      ⚠️ **如实边界**：本仓**无远端、无 `gh`**，CI **一次都不会触发**（083 的既有订正）⇒ 这一步是**为将来接 CI 准备的声明**，**不得**当作「门禁已上 CI」

## 阶段 C LoginPage 清零（提交 3）

- [x] T018 `pages/LoginPage.tsx` 清零 **13 处**（L34–37 卖点四条、L122 登录失败兜底、L239/243/246 品牌区、L432 点击刷新验证码、L437 `alt`、L438 `aria-label`、L443 点击获取、L476 演示账号：）
- [x] T019 `zh-CN.ts` / `en.ts` 加对应键：**zh 值逐字复制原字面量**（含全角标点、前导空格、`：`）；**en 值写真英文**
- [x] T020 `pages/LoginPage.test.tsx` 的断言改键名（`findByRole('img', { name: /验证码图片/ })` 一处）；
      **`:31` / `:54` 的中文只是用例标题，不动**
- [x] T021 `ZH_ALLOWED` 摘除 LoginPage 条目（count 13 → 0）
- [x] T022 **同批**摘 `check-ui.mjs` 的 **R6** LoginPage 条目（清零一落地，两本账都会因「count 少了」转红）

## 阶段 D 十个页面/组件清零（提交 4）

- [x] T023 `components/SignSection.tsx` 清零 **3 处**（L87 `alt="签名"`；L95 的**两个插值取值** `'报价单'` / `'合同'` —— 注意它们是 `t(…)` 的 `options`，不是 `t()` 的键）
- [x] T024 `pages/approval/ApprovalFlowPage.tsx` 清零 **3 处**（L47/48/49 **模块级常量表**）⇒ **表里改存键**、组件内 `t()`（范式见 `src/constants/enumLabels.ts`：值是英文键、中文只在注释）
- [x] T025 `pages/approval/ApprovalCenterPage.tsx` 清零 **2 处**（L251 审批详情、L289 `· 操作人 #`）
- [x] T026 `pages/opportunities/OpportunityListPage.tsx` 清零 **2 处**（L226/229 `suffix="元"`）
- [x] T027 `pages/orders/OrderListPage.tsx` 清零 **2 处**（L304 `emptyText`、L308 期次合计：¥）
- [x] T028 `pages/tasks/TaskCalendarPage.tsx` 清零 **2 处**（L78 `<Empty description="当日无任务" />`、L96 截止：）
- [x] T029 `pages/search/SearchResultPage.tsx` 清零 **1 处**（L45 `{ key: 'ALL', label: '全部' }`）
- [x] T030 `pages/stats/TeamLeaderboardPage.tsx` 清零 **1 处**（L20 `t ? t('…') : '未设目标'` —— **降级支**也要走 `t()`）
- [x] T031 i18n 键同批补齐（zh 逐字 / en 真英文）；`ZH_ALLOWED` 摘除上述 8 个文件的条目
- [x] T032 复跑 `node .i18n-keys/find-hardcoded-zh.mjs | awk '/^src./ { keep = ($0 !~ /usageMap|menuManifest|App.tsx|breadcrumbTrail/) } keep'` ⇒ 只剩 `App.tsx` 与**服务层 3 处**

## 阶段 E 服务层取词（提交 5 —— **第一个先例**）

- [x] T033 `services/visitService.ts` 清零 **2 处**（L42/51 两个 `new Error(...)`）—— 改走 `import i18n from '../i18n'` + `i18n.t(...)`
- [x] T034 `services/apiClient.ts` 清零 **1 处**（L78 `extractErrorMessage` 的**默认实参**）—— **默认实参在调用时求值** ⇒ 语言切换后取到当前语言，语义正确
- [x] T035 **先单跑** `apiClient.test.ts` 取读数、**再决定** `:54` 的断言是否改（`react-i18next` 的 mock 盖不住单例）—— **不许先假设**（`spec.md` FR-022）
- [x] T036 `ZH_ALLOWED` 摘除这两个文件；**提交信息里点名这是「服务层取词」的第一个先例**
- [x] T037 若 `pnpm lint` / 既有门禁因此报错 ⇒ **回退到台账登记**并在留痕里写明（`plan.md` 风险表），**不硬推**

## 阶段 F App.tsx 清零与 R6 收尾（提交 6）

- [x] T038 `App.tsx` L645 `aria-label="折叠/展开菜单"` 清零
- [x] T039 `App.tsx` L665/666 语言项改走**既有键** `app.language.zh` / `app.language.en`（**零新键**：两侧资源里 `zh` 的值本就都是 `'中文'`、`en` 都是 `'English'`，这是**语言自称 endonym、故意不译**，不是错误）
- [x] T040 `App.render.test.tsx:307,310` 的 `getByLabelText('折叠/展开菜单')` 改键名
- [x] T041 `ZH_ALLOWED` 的 `App.tsx` 条目 **count 57 → 55**（**不清零**：53 处路由 `name:` + 2 处真欠账仍在）
- [x] T042 **同批**摘 `check-ui.mjs` 的 **R6** App.tsx 条目
- [x] T043 `check-ui.mjs` 的 R6 块头注释**再订正一次**：`10 / 8` → **`8 / 6`**
      —— **原文逐字保留 + 追加带日期 ⚠️ 块 + 更新那两条 `awk` 复算命令**（照 097 T009–T011 体例）
- [x] T044 ⚠️ 块里写明：**摘的是两条 i18n 白名单，规则本身不动**，`MIN_CANDIDATES.R6`（1）与 `CANDIDATE_READINGS.R6`（10）**都不动**
      —— 后者数的是**裸写法命中数**、**不是台账大小**（摘完 2 条后它**仍是 10**）

## 阶段 G 数字落点与订正（提交 7）

- [x] T045 `PROJECT_FEATURES.md:19`（i18n 资源现值：`zh-CN` 行数 / `en` 行数 / 键数）改实测值
- [x] T046 `PROJECT_FEATURES.md:20`（Spec 模块现值 96 = 001–097 缺 069）→ **97（001–098，缺 069）**
- [x] T047 `CRM_FEATURE_COMPARISON.md:375` 的 **P1 #16 订正**：原文**逐字保留** + 带日期 ⚠️ 块写**实测 15 个文件 / 300 处**、「11」复现不出的推断、与本批处置（清零 34 处 / 台账 4 条 266 处 / 新门禁 `zh:check`）
- [x] T048 `CRM_FEATURE_COMPARISON.md:66` 与 `:82` 的 **91 / 94 已陈旧**（**非本批造成**）⇒ **顺手订正到真值**并**注明陈旧**（否则本批新增一个 spec 目录会让它更错）
- [x] T049 `specs/roadmap.md:448`、`specs/README.md` 的 096/097 行、`PROJECT_FEATURES.md:26` 的历史块 —— **一律原文保留、一字不动**（那是写下时的真实读数）
- [x] T050 **订正不静默的自查**：`quickstart.md` §6 的三条 `grep` **须全部非零命中**（**零命中 = 静默改写**）
- [x] T051 **键数的三个落点必须一致**：两份资源实测 = `PROJECT_FEATURES.md:19` = `specs/README.md` 的 098 行

## 阶段 H 勾选与留痕（交付时）

- [x] T052 **8 个破坏观测**（D1 / D2 / D3 / D4 两方向 / D5 / **D5-ii** / D6 / D7）逐条做、逐条**还原**（清单见 `quickstart.md` §3），留痕进 `falsification-evidence.md` §A–§F
      —— **D5-ii 是立项预设被实测推翻后补的**（`Min_CANDIDATES` 抓不住「摘掉 `JsxText`」，只掉 86 个候选点），见 `research.md` §5.1
- [x] T053 **口径边界自证**：独立复核一次「口径外另有 M 处」，与脚本印出的那行**必须相符**；不符则修脚本并重做（`falsification-evidence.md` §G）
- [x] T054 e2e：**跑了**给「444 处中文断言一行未改 + 全绿」；**没跑就如实写「未跑 e2e」并给理由**（无可用后端）—— **不得用门禁全绿冒充界面未变**
- [x] T055 门禁实跑读数进交付块（八道门禁逐条 exit 0；覆盖率对 33.6 / 47.2 / 21.4；`ui:check` R6 自身与冻结台账；`i18n:check` 键数）
- [x] T056 本文件勾选；`specs/README.md` 与 `specs/roadmap.md` 的 098 行状态改为交付态
- [x] T057 还原判据用 `git cat-file -p HEAD:<path> | sha1sum`（**内容级相等**）；**不称「逐字节一致」**（`core.autocrlf` 会让工作区副本 `sha1sum` 假不等）

---

## 实做订正（**交付时追加，上面任务原文逐字保留**）

1. **提交 4 的射程与计划表不同（16 处，不是 19 处）**。计划的分步表把服务层 3 处算进了「十个页面/组件」那一提交；实际执行按提交**边界更干净**的切法：提交 4 = **16 处 / 8 文件**，服务层 3 处**单独成提交 5**（因为它是「服务层取词」的第一个先例，值得一个独立可回退的提交）。合计仍为 **34 处 / 12 文件** ✓。
2. **D5-ii 的结果与立项预设相反**：`MIN_CANDIDATES` **抓不住**「摘掉 `JsxText`」，且该档**会假绿**（详见 `falsification-evidence.md` §E(ii) 的两次实跑）。已在 `check-zh.mjs` **原文保留旧断言 + 追加带日期 ⚠️ 订正块**；**本批不改口径**。
3. **`check-ui.mjs` 的 R6 块头「三个数」段一度写着 `条目数 = 7 / count 合计 = 9`，而机器读数是 `6 / 8`** —— 这是**提交 6 的漏改**（提交 6 只回改了两条 `awk` 命令的期望值）。提交 7 **就地改正现值**并追加**第五次带日期 ⚠️ 订正块**（原文逐字保留）。
4. **记号顺序的坑（照实记）**：T043 原文写的是 `10 / 8` → `8 / 6`，那是 **(count 合计 / 条目数)** 的顺序；而块头现值用的是 **(条目数 / count 合计)** ⇒ `条目数 = 6 / count 合计 = 8`。**两个记号顺序相反、不是矛盾**，读的时候以「哪个数在哪个词后面」为准。
5. **还原判据的实现与 T057 的字面略不同、等价**：T057 写的是 `git cat-file -p HEAD:<path> | sha1sum`；实际用的是 `git hash-object <file>` == `git rev-parse HEAD:<path>`（**内容级相等**，CRLF 免疫）**加** `git diff --quiet HEAD -- <file>`。两者判的都是**内容**，不是工作区字节。**仍然不称「逐字节一致」**。
6. **`scripts/check-zh.mjs` 的还原不能用「等于 HEAD」判**（它的两处 ⚠️ 注释订正是本批**有意未提交**的改动）⇒ 改判为「`node --check` 通过 + `pnpm zh:check` **exit 0** + 三行读数与破坏前**逐字相同**」。
7. **一处过程事故**：D1–D4 循环里一度用 `git checkout -- <file>` 还原，**把 `check-zh.mjs` 当时未提交的两处注释订正一起回退了**（`grep` 复查 0 命中才发现），**已重新应用并复验**；此后一律改用**备份副本 `cp` 回写**。教训：**破坏-还原循环绝不能 `git checkout` 带未提交有意改动的文件**。
8. **口径边界那两处措辞按实订正**（原文保留）：「位置」= **去重后的 (文件,行) 对**、**不是文件数**（9 个片段在 **6 个文件**里）；`RecycleBinPage` 的 `` ，N 条失败 `` 里的 **`N` 是插值、不是字面**。
9. **超出计划列表的一处顺手订正**：`README.md:163` 的 spec 计数（97 个功能模块，001~098）——**已在提交信息中披露**。
10. **e2e 未跑**（8081 无后端；仓规不得擅自重启可能归并行会话所有的共享后端）⇒ 见交付块与 `falsification-evidence.md` §I。

---

## 交付块（**交付后填**，立项阶段留空）

| 项 | 读数 |
|---|---|
| 清零 | **34 处 / 12 文件**（兑现；13 + 16 + 3 + 2 分四次提交，见「实做订正」1） |
| 冻结台账 | **4 条 / count 合计 266**（`awk` 复算 ✓；既有扫描器 `find-hardcoded-zh.mjs` 独立复核亦为 **266 处 / 4 文件**） |
| `zh:check` | 扫描 **269** 产品文件 / 候选点 **9158** / 判 **266 处、台账 4 条** / 口径外 **55**（只印不判）——**exit 0** |
| `ui:check` R6 自身 / 冻结台账 | R6 **6 条 / count 合计 8**（`awk` 复算 ✓）；冻结台账 **54 处，未增长** |
| 覆盖率（对 33.6 / 47.2 / 21.4） | `pnpm test:coverage` **EXIT=0**；**71.36 · 75.26 · 39.24 · 71.36**（对阈值 **33.6 / 47.2 / 21.4**，**未下调**）。⚠️ **Branch 在同一次交付里出现过两个读数**：本次 **75.26**、同树另一次 **75.27**（**差 0.01，原因未查明**）——**不为它改代码、也不为它改阈值**，见 `falsification-evidence.md` §I |
| 前端用例数 | **91 文件 / 464 用例（全部通过）**，Duration **160.95s** |
| i18n 键数（三个落点） | **2963 / 2963**（`i18n:check` 实测 = `PROJECT_FEATURES.md:19` = `specs/README.md` 的 098 行） |
| e2e | **未跑** —— `curl` 8081 `/actuator/health` **HTTP 000**（无后端在跑）；**不用门禁绿冒充界面未变**，替代证据见 `falsification-evidence.md` §I |
| 定向破坏 | **8 个观测**（D1 / D2 / D3 / D4(i) / D4(ii) / D5 / D5-ii(a)(b) / D6 / D7）**逐条观测转红、逐条还原**，留痕见 `falsification-evidence.md` |
| 提交 | **7 次**（立项 / 门禁 / LoginPage / 十页与组件 / 服务层 / App.tsx 与 R6 / 数字落点与勾选） |

⚠️ **本项不写「本次提交」的哈希**（写进提交自己携带的文件会在 `--amend` 后变成不存在的对象）。
