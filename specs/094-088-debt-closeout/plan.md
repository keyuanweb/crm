# Implementation Plan: 088 收口三笔账（094）

**Branch**: `094-088-debt-closeout` ｜ **Spec**: `spec.md` ｜ **Research**: `research.md`

## 技术上下文

- **栈**：React 18 + TypeScript + antd 5.22 + Vite（前端）；门禁脚本是 Node ESM（`frontend/scripts/*.mjs`）。
- **形制**：**改造类**——**零后端改动、无迁移、无契约变更、无 data-model**。
  动 3 个生产 tsx 的 4 个属性 + 1 个门禁脚本；其余全是记录订正与登记。
- **不动的东西**：`specs/093-dashboard-truthfulness/`（另一会话在飞）、`.specify/feature.json`（共享单槽指针）、
  `frontend/src/index.css`、`playwright.config.ts`、`package.json`（**不新增依赖**）。

## 做法

### 第 1 步：③ 三处补做（`signSection` / `surveyBlock` / portal 服务状态块）

逐处只改两个属性，**逐处可独立回退**：

| 文件 | 行锚（改前） | 改法 |
|---|---|---|
| `frontend/src/components/SignSection.tsx` | `<Descriptions column={2} size="small">` | `column={{ xs: 1, sm: 2, md: 3 }}`；同块全宽项 `span={2}` → `span={3}` |
| `frontend/src/components/SurveyBlock.tsx` | `<Descriptions column={2} size="small">` | 同上 |
| `frontend/src/pages/portal/CustomerPortalPage.tsx` | 服务状态块 `<Descriptions column={3} bordered size="small">` | 同上；该块提交时间项 `span={2}` → `span={3}` |

**锚字符串、不用行号**（本仓教训：行号会腐坏）。改完**必须**有一条 grep 读数：
全库 `column={数字}` 只剩 `column={1}` 那 1 处例外。

**不做**：`PersonalCenterPage` ×2（已合规）、portal 结果面板 `column={1}`（设计决定）。
理由写进 FR-094-003 的订正块。

### 第 2 步：② + R8 —— 改 `frontend/scripts/check-ui.mjs`

三件事，**同一处收口**（都是这个脚本的自检/信息层）：

1. **零候选失败信息二分**（`MIN_CANDIDATES` 分支的 `fix` 文案）：
   给出两种成因 + 各自处置 + 该规则的**历史实测候选数**（比对用）。
   **不改 `MIN_CANDIDATES` 的任何数值**——改数值就是放行。
2. **`MIN_CANDIDATES` 逐条加实测注释**：其中 `R3` 写明「88 / 1 / 58」与「对象已被 FormGrid 取代殆尽」，
   并挂到文件头 R2/R3 的毕业记录上（那里正是「假红」误判的来源）。
3. **新增 R8**：`Descriptions` 的 `column` 不得写死为**大于 1 的数字**。
   - 判据：`scanTagEvents(code, 'Descriptions')` 的非 close 事件（**逐字符扫描，跨行开标签同样命中**）；
     `attrOf(text, 'column')` 取出值，`/^\s*\{/` = 断点对象（放行）、`/^\s*\d/` 且数值 > 1 = **命中**、
     `= 1` 放行、属性缺失放行（antd 的 `DEFAULT_COLUMN_MAP` 本身就是响应式的）。
   - 档位：`allowed: null`（**零容忍、无白名单**，与 R2/R3 同档）；`MIN_CANDIDATES.R8 = 12`（今天 14，见 research §3）。
   - 失败信息的 `fix` 要给**可粘贴的修法**（`column={{ xs: 1, sm: 2, md: 3 }}` + 全宽项 `span={3}`），
     并说明这是 FR-015 的字母（088 的 SC-006 也要求错误信息自带修复指引）。
   - **写清边界**（注释里）：只判**字面量数字**；`column={someVar}`、`{...spread}` 判不到——**这是源码级护栏，不是渲染级**。

### 第 3 步：① 作废留痕（**只改文档**）

在 088 的 `spec.md` / `plan.md` / `tasks.md` 的 **6** 处，各加 ⚠️ 订正块（**原文保留不删**），
内容：做不到（`TS2322` 实测）＋ 类型定义出处 ＋ 三条真实路径 ＋ 本项不做任何一条。
**不得**用 `as any` / `@ts-expect-error` 绕过——那是关掉类型检查，不是收口。

### 第 4 步：FR-015 措辞订正（088 `spec.md` 原文保留 + ⚠️ 订正）

把「一律」收窄为「详情页与详情区块的 `Descriptions`」，并**逐条列出例外**（见 FR-094-003），
同时把**本项新增的 R8 护栏**回填进 FR-015（它从此**有**门禁了——原记录写的是「既没有门禁规则、也没有独立自动化用例」，
那句话从本项起**只有后半句成立**，须订正）。

### 第 5 步：两处登记 + 收尾

`specs/README.md` 模块表插 094 行；`specs/roadmap.md` 的 `## 当前进度` 插行、整体覆盖度与 `**最后更新**` 同步。

## 验证

1. **七道门禁**（088 SC-003 同款）：
   ```bash
   cd frontend && pnpm typecheck && pnpm lint && pnpm i18n:check && pnpm menu:check && pnpm perms:check && pnpm ui:check && pnpm test:coverage
   ```
   `ui:check` 收尾读数「白名单内冻结的既存债 **53** 处」（R8 零容忍不进白名单 ⇒ 该数不变）。
2. **A/B 对拍**（FR-094-002 的读数部分）：临时探针**同一次运行内**对拍「合成块矩阵（column × span 四组合）」与「两个真实组件的旧态/新态」，
   读 **dev 告警条数与 `colspan` 格位**（jsdom 只到 3 列档）；旧态用**临时还原真实组件**取得，还原后 `sha256sum -c` 核对逐字节回原样。
   漏掉「格位」这一半就只剩告警数，会看不见 `column` 改写带来的结构变化——**094 初稿正是这样漏掉 md 档的可见变化**。
3. **R8 定向破坏**（SC-094-002）：往一个真 `Descriptions` 写回 `column={2}` ⇒ `ui:check` 红在 R8 ⇒ 逐字节还原 ⇒ 绿。
   **破坏逐个做、逐个还原，破坏期间不得提交**（本仓纪律）。
4. **候选归零留痕**（SC-094-003）：临时把 R8 的候选扫不到（探针）⇒ 失败信息出现两种成因与处置 ⇒ 还原。
5. **静态读数**（SC-094-004）：`Descriptions` 开标签 14 / 写死多列 0 / `column={1}` 1。

## 风险与处置

| 风险 | 处置 |
|---|---|
| 改写 `span` 后像素真的变了（推演的 clamp 语义不成立） | A/B 读数与推演对拍；**不符就以读数为准并改写 research §5**，把可见变化如实记入 SC-005 欠账。**已执行**：`span` 改写实测为中性（告警 0→2），但**推演里「md 及以上列数不变」被推翻**（`SignSection`/`SurveyBlock` 旧写 `column={2}` ⇒ md 档 2→3 列，是可见变化）——research §5.3、spec FR-094-002/SC-094-006 均已按读数改写 |
| R8 误伤合法写法 | 判据只咬「>1 的字面量数字」；`{1}`、断点对象、缺省、变量一律放行；**定向破坏**证明它真会红 |
| `MIN_CANDIDATES.R8 = 12` 太紧、日后合法删区块就红 | 已注明「12 = 今天 14 − 2」；红时的处置是**核对读数后刷新下限**（与白名单同一条纪律：读数以实测为准） |
| 与并行会话撞车 | 提交前 `ListAgents`；**逐路径 `git add`**；不碰 093、不写 `.specify/feature.json` |
| 本批产生新的可见变化 | **如实披露**为新的 SC-005 欠账（SC-094-006），不声称已获视觉背书 |

## 提交划分（一次收口，两个提交）

1. `refactor(094): 详情区块列数三处补做，R8 护栏咬住写死的多列`——3 个 tsx + `check-ui.mjs`（R8 + 诊断 + 注释）
2. `docs(094): 作废 span="filled" 收口，订正 FR-015 措辞与 088 的三笔账`——088 三份文档 + 本项产物 + 两处登记

> 拆两个的理由：**代码/护栏**与**记录订正**的受众与回退粒度不同。护栏那条单独可回退，不牵动任何生产行为。
