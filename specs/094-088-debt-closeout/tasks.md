# Tasks: 088 收口三笔账（094）

**Spec**: `spec.md` ｜ **Plan**: `plan.md` ｜ **Research**: `research.md` ｜ **凭据**：`falsification-evidence.md`

> 纪律照旧：**破坏逐个做、逐个还原，破坏期间不得提交**；**只报「全绿」不构成证据**；
> 提交**逐路径 `git add`**，禁用 `git add -A`；临时探针 untracked、用完即删。

---

## Phase 1：立项与产物

- [ ] **T001** 手写本项产物（`spec.md` / `plan.md` / `research.md` / `tasks.md` / `quickstart.md` /
      `checklists/requirements.md`）。**不跑 `/speckit-*`**——`.specify/feature.json` 是并行会话共用的单槽指针
      （现存 `specs/092-geometry-e2e-guard`），写它会打断别人。
- [ ] **T002** 确认号位：`specs/093-dashboard-truthfulness/` 是**另一会话的未提交在飞工作**（`git ls-files` 无、`??`）；
      本项取 **094**，全程不碰其任何文件。

## Phase 2：③ 三处补做（生产代码，逐处独立可回退）

- [ ] **T003** `frontend/src/components/SignSection.tsx`：`column={2}` → `column={{ xs: 1, sm: 2, md: 3 }}`；
      同块全宽项 `span={2}` → `span={3}`。锚字符串定位，**不用行号**。
- [ ] **T004** `frontend/src/components/SurveyBlock.tsx`：同上。
- [ ] **T005** `frontend/src/pages/portal/CustomerPortalPage.tsx` 的**服务状态块**：`column={3}` → 断点对象；
      该块提交时间项 `span={2}` → `span={3}`。**不动**同文件的查询结果面板（`column={1}`，设计决定）。
- [ ] **T006** 静态读数复核（SC-094-004）：全库 `Descriptions` 开标签 **14**、`column={数字}` 只剩 **1**（`{1}` 例外）、
      写死多列 **0**。读数写进 `falsification-evidence.md`。

## Phase 3：② + R8（门禁脚本）

- [ ] **T007** `frontend/scripts/check-ui.mjs`：新增 **R8**（`Descriptions` 的 `column` 不得写死为 >1 的数字），
      `allowed: null` 零容忍；`MIN_CANDIDATES.R8 = 12`（今天 14；注释写明 12 > 10 = 「只认单行开标签」那一档失效的水位）；
      失败信息的 `fix` 给可粘贴修法 + 边界声明（只判字面量数字，变量/spread 判不到）。
- [ ] **T008** 同文件的**零候选失败信息二分**：`fix` 文案给出①扫描器失效 → 修扫描器、②规则已无可判对象 →
      按 T045/R2 先例**退役该规则**；并打印该规则的**历史实测候选数**。**`MIN_CANDIDATES` 数值一个都不改。**
- [ ] **T009** `MIN_CANDIDATES` 逐条加实测注释；`R3` 一条写明「`<Col` 全库 88 / 表单内 1 / `<FormGrid` 58」
      与「对象已被 FormGrid 取代殆尽」；并将「假红」误判的定性订正挂到文件头 R2/R3 的毕业记录处。

## Phase 4：① 作废 + FR-015 订正（只改文档）

- [ ] **T010** 088 的 `spec.md` / `plan.md` / `tasks.md` 共 **6** 处 `span="filled"` 收口记录，逐处加 ⚠️ 订正
      （做不到 + `TS2322` 实测 + 类型定义出处 + 三条真实路径 + 本项都不做）。**原文一律保留不删**。
      落点锚：`plan.md` 的「全宽项改 `span="filled"`」与「是同语义且**不告警**的写法」；
      `tasks.md` 的「（antd 的 `span="filled"` 也是同一语义…）」「收口办法（覆盖两条 Track…）」「1. **`span="filled"` 收口**」；
      `spec.md` 的「收口办法 `span="filled"` 属另一项」。
- [ ] **T011** 088 `spec.md` 的 **FR-015** 加 ⚠️ 订正：收窄「一律」为「详情页与详情区块的 `Descriptions`」；
      列出两处例外并给理由（`PersonalCenterPage` ×2 已合规、portal 结果面板 `column={1}` 是设计决定）；
      回填**本项新增的 R8 门禁**（原记录的「既没有门禁规则、也没有独立的自动化用例」从本项起只有后半句成立）。
- [ ] **T012** 088 的 T044 遗留项 2（原报 5 处）就地订正为 **3 处真违规 + 2 处已合规/非违规**，
      写明是**判据收紧**造成的缩水（按「字形相似」列 vs 按「是否构成窄屏缺陷」判），不是「两处逃掉了」。

## Phase 5：验证、登记、收尾

- [ ] **T013** A/B 告警计数（FR-094-002 的读数）：改动前后各跑一次受影响页面的既有用例文件，捕获 dev 告警条数，
      与 research §5 的推演对拍；**不符则以读数为准并改写 research §5**。读数进 `falsification-evidence.md`。
- [ ] **T014** 七道门禁全绿（`typecheck` / `lint` 零 warning / `i18n:check` / `menu:check` / `perms:check` /
      `ui:check` / `test:coverage`）；用例数/文件数**不少于**基线 83 文件 / 398 用例。
- [ ] **T015** **R8 定向破坏**（`v`erify → 还原）：写回一处 `column={2}` ⇒ `ui:check` 必须红在 **R8** 且退出码非零
      ⇒ 逐字节还原（`sha256sum -c` 核对）⇒ 转绿。留痕进 `falsification-evidence.md`。
- [ ] **T016** **候选归零留痕**（SC-094-003）：临时让 R8 扫不到候选 ⇒ 失败信息出现**两种成因与各自处置**
      ⇒ 还原。留痕同上。
- [ ] **T017** 两处登记：`specs/README.md` 模块表插 094 行；`specs/roadmap.md` 的 `## 当前进度` 插行 +
      整体覆盖度 + `**最后更新**`（088 那笔 SC-005 后的新欠账要写进去）。
- [ ] **T018** 收尾：临时探针删除、`git status` 只剩**他人的**改动（`frontend/src/i18n/{en,zh-CN}.ts` +
      `specs/093-*`）；`ListAgents` 确认无会话在写本批文件；**逐路径 `git add`** 分两个提交（见 plan 的提交划分）。

---

## 遗留（本项明确不做，记下来不静默）

1. **`Descriptions` 的 `items` API 现代化**：三条真实路径里最正的那条（能逐个断点表达 `span`、能 `'filled'`），
   但会改动正文渲染与既有用例 ⇒ 另立一项。**本项作的 3 处 `span={3}` 写法会在 dev 告警**，
   该告警的根治只能靠这一项。
2. **渲染级几何用例**：本项只到**源码级**护栏（R8）。几何量属 092 的 Playwright 家族——
   088 留下的「P3 版式无用例守护」仍在原处。
3. **新的 SC-005 欠账**：本项 3 处在 **sm/xs** 档的列数有可见变化（md 及以上不变），须由用户复看；
   在此之前**不得**读成「已获视觉背书」。
