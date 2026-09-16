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

- [ ] T001 写 `spec.md` / `plan.md` / `research.md` / `quickstart.md` / `falsification-evidence.md` / `tasks.md` / `checklists/requirements.md`
- [ ] T002 `research.md` 必须装齐三件：① 两套口径对照表 + 「11」的推断；② **台账四条理由的逐条复核**；③ **服务层取词先例**的立项说明；**外加盟立期选项数字的订正**（37/13 → 34/12）
- [ ] T003 `specs/README.md` 模块表加 098 行（**状态列如实写「⏳ 进行中」**，**不预勾**）
- [ ] T004 `specs/README.md` 的**编号说明**纳入 098（**按文本锚定位，不按行号**——行号会漂），并**如实写明 `092`~`097` 未在该段逐项登记**（该段记录的是编号异常与需点明的形制，**不是全量索引**）
- [ ] T005 `specs/roadmap.md` 的 `## 当前进度` 加 098 行，**勾选框留空、不预勾**（照 095/096/097 先例）

## 阶段 B 门禁与冻结台账（提交 2 —— 本项的结构性交付）

- [ ] T006 新建 `frontend/scripts/check-zh.mjs`：**逐字复用**既有 AST 扫描器的口径（`ts.isStringLiteral` + `ts.isNoSubstitutionTemplateLiteral` + `ts.isJsxText`（`trim()` 非空）、CJK `/[一-鿿]/`、跳过 `import`、排除 `src/i18n/**` 与 `*.test.*`）
- [ ] T007 照 `check-ui.mjs` 的形制组织：`ruleDefs` 式结构、`{ hits, candidates }` 返回形态、`ZH_ALLOWED = [{ file, count, reason }]`（**reason 必填散文**）
- [ ] T008 台账理由必须写明**类别**（生成物 / 路由元数据 / 演示数据 / 降级兜底）**与其中是否含欠账**；含欠账的**必须点名**（`App.tsx` 条目点名 2 处）
- [ ] T009 **台账第一版登记全部 15 个文件 / 300 处**（= 现状）⇒ 门禁**落地即绿**，同时证明台账**覆盖了现状**（未登记的命中一条都没有）
- [ ] T010 **三分支双向校验**：未登记命中 → 红；某条 `count` **多了** → 红；某条 `count` **少了（含已归零）** → 红
- [ ] T011 `MIN_CANDIDATES.ZH`（**被执行的反假绿下限**）与 `CANDIDATE_READINGS.ZH`（只用于区分「扫描器坏了」与「规则该退役」）
- [ ] T012 **口径边界自证**（信息性）：同一批 AST 节点跑一遍放宽口径（全角标点 + 全角字母数字 + 收 `TemplateExpression`），打印「本脚本判 N；口径外另有 M 处」；**不进 `problems`、不改退出码**
- [ ] T013 `--list` 开关：逐文件逐行清单（= 本批工作单，也是下一批的入口）
- [ ] T014 失败汇报按 `【kind】N 处` 分组 + `第 N 行：<text>` + **`修复：<fix>`**
- [ ] T015 **文件头点名「服务层取词」是仓里第一个先例**（`i18n.t(` 全仓零命中），并写明边界：**只用于「非组件模块拿不到 hook」这一种情形**
- [ ] T016 `frontend/package.json` 加 `"zh:check": "node scripts/check-zh.mjs"`
- [ ] T017 `.github/workflows/ci.yml` 加一步 `zh:check`（与既有步骤并列）
      ⚠️ **如实边界**：本仓**无远端、无 `gh`**，CI **一次都不会触发**（083 的既有订正）⇒ 这一步是**为将来接 CI 准备的声明**，**不得**当作「门禁已上 CI」

## 阶段 C LoginPage 清零（提交 3）

- [ ] T018 `pages/LoginPage.tsx` 清零 **13 处**（L34–37 卖点四条、L122 登录失败兜底、L239/243/246 品牌区、L432 点击刷新验证码、L437 `alt`、L438 `aria-label`、L443 点击获取、L476 演示账号：）
- [ ] T019 `zh-CN.ts` / `en.ts` 加对应键：**zh 值逐字复制原字面量**（含全角标点、前导空格、`：`）；**en 值写真英文**
- [ ] T020 `pages/LoginPage.test.tsx` 的断言改键名（`findByRole('img', { name: /验证码图片/ })` 一处）；
      **`:31` / `:54` 的中文只是用例标题，不动**
- [ ] T021 `ZH_ALLOWED` 摘除 LoginPage 条目（count 13 → 0）
- [ ] T022 **同批**摘 `check-ui.mjs` 的 **R6** LoginPage 条目（清零一落地，两本账都会因「count 少了」转红）

## 阶段 D 十个页面/组件清零（提交 4）

- [ ] T023 `components/SignSection.tsx` 清零 **3 处**（L87 `alt="签名"`；L95 的**两个插值取值** `'报价单'` / `'合同'` —— 注意它们是 `t(…)` 的 `options`，不是 `t()` 的键）
- [ ] T024 `pages/approval/ApprovalFlowPage.tsx` 清零 **3 处**（L47/48/49 **模块级常量表**）⇒ **表里改存键**、组件内 `t()`（范式见 `src/constants/enumLabels.ts`：值是英文键、中文只在注释）
- [ ] T025 `pages/approval/ApprovalCenterPage.tsx` 清零 **2 处**（L251 审批详情、L289 `· 操作人 #`）
- [ ] T026 `pages/opportunities/OpportunityListPage.tsx` 清零 **2 处**（L226/229 `suffix="元"`）
- [ ] T027 `pages/orders/OrderListPage.tsx` 清零 **2 处**（L304 `emptyText`、L308 期次合计：¥）
- [ ] T028 `pages/tasks/TaskCalendarPage.tsx` 清零 **2 处**（L78 `<Empty description="当日无任务" />`、L96 截止：）
- [ ] T029 `pages/search/SearchResultPage.tsx` 清零 **1 处**（L45 `{ key: 'ALL', label: '全部' }`）
- [ ] T030 `pages/stats/TeamLeaderboardPage.tsx` 清零 **1 处**（L20 `t ? t('…') : '未设目标'` —— **降级支**也要走 `t()`）
- [ ] T031 i18n 键同批补齐（zh 逐字 / en 真英文）；`ZH_ALLOWED` 摘除上述 8 个文件的条目
- [ ] T032 复跑 `node .i18n-keys/find-hardcoded-zh.mjs | awk '/^src./ { keep = ($0 !~ /usageMap|menuManifest|App.tsx|breadcrumbTrail/) } keep'` ⇒ 只剩 `App.tsx` 与**服务层 3 处**

## 阶段 E 服务层取词（提交 5 —— **第一个先例**）

- [ ] T033 `services/visitService.ts` 清零 **2 处**（L42/51 两个 `new Error(...)`）—— 改走 `import i18n from '../i18n'` + `i18n.t(...)`
- [ ] T034 `services/apiClient.ts` 清零 **1 处**（L78 `extractErrorMessage` 的**默认实参**）—— **默认实参在调用时求值** ⇒ 语言切换后取到当前语言，语义正确
- [ ] T035 **先单跑** `apiClient.test.ts` 取读数、**再决定** `:54` 的断言是否改（`react-i18next` 的 mock 盖不住单例）—— **不许先假设**（`spec.md` FR-022）
- [ ] T036 `ZH_ALLOWED` 摘除这两个文件；**提交信息里点名这是「服务层取词」的第一个先例**
- [ ] T037 若 `pnpm lint` / 既有门禁因此报错 ⇒ **回退到台账登记**并在留痕里写明（`plan.md` 风险表），**不硬推**

## 阶段 F App.tsx 清零与 R6 收尾（提交 6）

- [ ] T038 `App.tsx` L645 `aria-label="折叠/展开菜单"` 清零
- [ ] T039 `App.tsx` L665/666 语言项改走**既有键** `app.language.zh` / `app.language.en`（**零新键**：两侧资源里 `zh` 的值本就都是 `'中文'`、`en` 都是 `'English'`，这是**语言自称 endonym、故意不译**，不是错误）
- [ ] T040 `App.render.test.tsx:307,310` 的 `getByLabelText('折叠/展开菜单')` 改键名
- [ ] T041 `ZH_ALLOWED` 的 `App.tsx` 条目 **count 57 → 55**（**不清零**：53 处路由 `name:` + 2 处真欠账仍在）
- [ ] T042 **同批**摘 `check-ui.mjs` 的 **R6** App.tsx 条目
- [ ] T043 `check-ui.mjs` 的 R6 块头注释**再订正一次**：`10 / 8` → **`8 / 6`**
      —— **原文逐字保留 + 追加带日期 ⚠️ 块 + 更新那两条 `awk` 复算命令**（照 097 T009–T011 体例）
- [ ] T044 ⚠️ 块里写明：**摘的是两条 i18n 白名单，规则本身不动**，`MIN_CANDIDATES.R6`（1）与 `CANDIDATE_READINGS.R6`（10）**都不动**
      —— 后者数的是**裸写法命中数**、**不是台账大小**（摘完 2 条后它**仍是 10**）

## 阶段 G 数字落点与订正（提交 7）

- [ ] T045 `PROJECT_FEATURES.md:19`（i18n 资源现值：`zh-CN` 行数 / `en` 行数 / 键数）改实测值
- [ ] T046 `PROJECT_FEATURES.md:20`（Spec 模块现值 96 = 001–097 缺 069）→ **97（001–098，缺 069）**
- [ ] T047 `CRM_FEATURE_COMPARISON.md:375` 的 **P1 #16 订正**：原文**逐字保留** + 带日期 ⚠️ 块写**实测 15 个文件 / 300 处**、「11」复现不出的推断、与本批处置（清零 34 处 / 台账 4 条 266 处 / 新门禁 `zh:check`）
- [ ] T048 `CRM_FEATURE_COMPARISON.md:66` 与 `:82` 的 **91 / 94 已陈旧**（**非本批造成**）⇒ **顺手订正到真值**并**注明陈旧**（否则本批新增一个 spec 目录会让它更错）
- [ ] T049 `specs/roadmap.md:448`、`specs/README.md` 的 096/097 行、`PROJECT_FEATURES.md:26` 的历史块 —— **一律原文保留、一字不动**（那是写下时的真实读数）
- [ ] T050 **订正不静默的自查**：`quickstart.md` §6 的三条 `grep` **须全部非零命中**（**零命中 = 静默改写**）
- [ ] T051 **键数的三个落点必须一致**：两份资源实测 = `PROJECT_FEATURES.md:19` = `specs/README.md` 的 098 行

## 阶段 H 勾选与留痕（交付时）

- [ ] T052 **8 个破坏观测**（D1 / D2 / D3 / D4 两方向 / D5 / **D5-ii** / D6 / D7）逐条做、逐条**还原**（清单见 `quickstart.md` §3），留痕进 `falsification-evidence.md` §A–§F
      —— **D5-ii 是立项预设被实测推翻后补的**（`Min_CANDIDATES` 抓不住「摘掉 `JsxText`」，只掉 86 个候选点），见 `research.md` §5.1
- [ ] T053 **口径边界自证**：独立复核一次「口径外另有 M 处」，与脚本印出的那行**必须相符**；不符则修脚本并重做（`falsification-evidence.md` §G）
- [ ] T054 e2e：**跑了**给「444 处中文断言一行未改 + 全绿」；**没跑就如实写「未跑 e2e」并给理由**（无可用后端）—— **不得用门禁全绿冒充界面未变**
- [ ] T055 门禁实跑读数进交付块（八道门禁逐条 exit 0；覆盖率对 33.6 / 47.2 / 21.4；`ui:check` R6 自身与冻结台账；`i18n:check` 键数）
- [ ] T056 本文件勾选；`specs/README.md` 与 `specs/roadmap.md` 的 098 行状态改为交付态
- [ ] T057 还原判据用 `git cat-file -p HEAD:<path> | sha1sum`（**内容级相等**）；**不称「逐字节一致」**（`core.autocrlf` 会让工作区副本 `sha1sum` 假不等）

---

## 实做订正（**交付时追加，上面任务原文逐字保留**）

（交付时填：立项预设与实际执行的偏差逐条留痕）

---

## 交付块（**交付后填**，立项阶段留空）

| 项 | 读数 |
|---|---|
| 清零 | （交付时填：**34 处 / 12 文件**是否兑现；实际值） |
| 冻结台账 | （交付时填：条目数 / count 合计） |
| `zh:check` | （交付时填） |
| `ui:check` R6 自身 / 冻结台账 | （交付时填，期望 R6 **6 条 / count 合计 8**） |
| 覆盖率（对 33.6 / 47.2 / 21.4） | （交付时填；Branch **单列一节写明 0.01 差异原因未查明**） |
| 前端用例数 | （交付时填） |
| i18n 键数（三个落点） | （交付时填） |
| e2e | （交付时填：跑了给读数；**没跑如实写「未跑」+ 理由**） |
| 定向破坏 | （交付时填：**8 个观测**逐条转红、逐条还原，留痕见 `falsification-evidence.md`） |
| 提交 | （交付时填：**7 次**） |

⚠️ **本项不写「本次提交」的哈希**（写进提交自己携带的文件会在 `--amend` 后变成不存在的对象）。
