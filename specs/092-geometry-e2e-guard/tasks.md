# Tasks: 列表页与窄屏外壳几何的端到端护栏

**Feature Branch**: `092-geometry-e2e-guard`

**Input**: Design documents from `/specs/092-geometry-e2e-guard/`

**Prerequisites**: [plan.md](./plan.md)（必需）、[spec.md](./spec.md)（必需）、[research.md](./research.md)、[quickstart.md](./quickstart.md)

**Tests**: 本项**就是**测试——交付物是三份 Playwright 文件（1 个 helper + 2 个 spec），
不存在「先写测试再写实现」的分层。每个用户故事阶段即一份 spec 文件。

**形制**：纯前端新增测试。**零后端改动、无 Flyway 迁移、无契约变更、无 data-model。**

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可与本阶段其它任务并行（不同文件、无未完成依赖）
- **[Story]**: 该任务归属的用户故事（US1 / US2 / US3）
- 每条任务都带**确切文件路径**

## 路径约定

- **Web 应用**：`backend/`、`frontend/`；本项**只动** `frontend/e2e/`
- 规格文档：`specs/092-geometry-e2e-guard/`

---

## Phase 1: Setup（前置核对与基线）

**Purpose**：本项的第一条纪律是「跑绿不构成证据」，所以先固定**改动前的状态**，
后面每一次「转绿」才有比较对象。

- [ ] T001 核对前置：确认 `.specify/feature.json` 指向 `specs/092-geometry-e2e-guard`；确认 `frontend/playwright.config.ts` 与 `frontend/src/` 下生产代码**未被本项改动**（`git status` 只应出现并行会话既有的两个文件）；确认 `frontend/e2e/` 下现有 5 个 spec 与 `helpers/login.ts` 在场；按 `specs/092-geometry-e2e-guard/quickstart.md` §0 核对后端 8081 已在跑且**验证码关闭**，并**比对后端进程启动时间与 `backend/target/classes` 下 `.class` 的 mtime**（本仓既有规矩：e2e 打的是「已经在跑的后端」，不一定是本次改动）
- [ ] T002 记录两项基线，写进 `specs/092-geometry-e2e-guard/falsification-evidence.md` 的「基线」节：① `cd frontend && pnpm run test` 的**单元测试总数与通过数**（SC-EG-007 要求总数不减少）；② `cd frontend && pnpm run test:e2e` 的**既有 5 个文件的通过/失败情况**（SC-EG-001 要求不新增失败）

**Checkpoint**：基线与前置固定完毕，可以开始写 helper。

---

## Phase 2: Foundational（共享 helper，阻塞全部用户故事）

**Purpose**：`frontend/e2e/helpers/geometry.ts` 是本项**唯一有实质意义的抽象**——
两个 spec 的量取口径**只能有一份**，因为「两个文件的判据口径不一致」正是 091 刚订正过的那类缺陷（[plan.md](./plan.md) 的 Structure Decision）。

**⚠️ CRITICAL**：本阶段不完成，任何用户故事都不得开工。

- [ ] T003 创建 `frontend/e2e/helpers/geometry.ts` 的常量与类型层：导出 `VIEWPORT_WIDE = { width: 1280, height: 720 }` 与 `VIEWPORT_NARROW = { width: 375, height: 812 }`（FR-EG-003：视口由用例显式设定，不依赖框架默认值）；导出 `SCROLLBAR_TOLERANCE_PX = 16`（**必须写明理由注释**：无头 Chromium 实测占位为 0，经典滚动条平台约 15px，向上取整；缺陷态是 24，与此容差相差一个数量级，不会被掩盖）；导出 `PIXEL_TOLERANCE_PX = 1`（半像素布局的取整容差）；导出活性下限常量的类型化定义（短页 ≥2、长页 ≥1、窄屏 ≥4）
- [ ] T004 在 `frontend/e2e/helpers/geometry.ts` 实现**两阶段等待** `waitForGeometryStable(page)`（FR-EG-002 的「不得 flake」）：阶段一「要素齐备」= `.page-scroll` + `.ant-layout-footer` + `.ant-layout-content` + 页面根卡片**四者在场**且 `.ant-spin-spinning` **不存在**；阶段二「几何自稳定」= 每 **100ms** 重读一遍判据所需的全部输入（四舍五入到整像素），**连续两次读数完全相同**才继续，**8s 超时 ⇒ 抛错 ⇒ 用例红**。**MUST NOT** 用固定 `waitForTimeout`、**MUST NOT** 用 `waitForLoadState('networkidle')`（依据 [research.md](./research.md) §2：卡片 67–107ms 就出现，但几何要到 400–500ms 才定）
- [ ] T005 在 `frontend/e2e/helpers/geometry.ts` 实现**列表页量取** `readListPageGeometry(page)`：返回滚动盒底边、滚动盒自身下内边距、内容盒底边、内容区下内边距、页脚**实测高度**、页面根卡片底边（ProTable 页取 `.ant-pro-card:not(.ant-pro-table-search)`，Card 页取根卡片）、**卡片自身下外边距**、滚动盒 `scrollHeight` / `clientHeight`、文档根可视宽与可滚宽度、以及卡片是否溢出。**页脚高度必须现量**（FR-EG-006），**MUST NOT** 写死 36 / 56 / 664 / 624
- [ ] T006 在 `frontend/e2e/helpers/geometry.ts` 实现两个判据函数与两个比值口径：`fillOf(geom)` = `滚动盒底边 − 滚动盒自身下内边距 − 卡片底边 − 卡片自身下外边距`（FR-EG-005）；`residualOf(geom)` = `(视口底边 − 卡片底边) − (页脚实测高 + 内容区下内边距 + 卡片自身下外边距)`（FR-EG-006）；**并**在文件顶部注释里写明「这两个量是两个不同的量，不得互换」（沿用 091 订正后的口径）
- [ ] T007 在 `frontend/e2e/helpers/geometry.ts` 实现**窄屏量取** `readNarrowShellGeometry(page)`：内容区可见宽度（`.page-scroll` 的 `clientWidth`，**含其自身左右内边距**）、内容容器宽度（`.page-container` 的内容盒宽）、菜单容器（锚点 = **`.ant-menu` 的直接父元素**，依据 research §5）的宽与高、**并读取该锚点的 `parentElement`** 供「它是不是外壳的直接子元素」这条断言使用；文档根横向溢出 = `documentElement.scrollWidth − innerWidth`
- [ ] T008 在 `frontend/e2e/helpers/geometry.ts` 实现**报告与断言辅助**（FR-EG-010 / FR-EG-013 / SC-EG-008）：`describePage(path, values)` 逐页打印全部实测值（人不必读源码即可核对「它到底测了什么」）；`softExpect(condition, { page, quantity, actual, expected })` 生成带**页面路径 + 量名 + 实测值与应达值**的 soft 断言；`assertLiveness(bucketName, count, floor)` **硬断言**样本数下限，失败信息明确写「`{bucketName}` 样本数为 `{count}`，低于下限 `{floor}`」
      —— ⚠️ `assertLiveness` **MUST NOT** 有任何「count === 0 就跳过」的分支：**零样本必须是红的**（FR-EG-010 / FR-EG-011）

**Checkpoint**：helper 就绪，两个 spec 可以开工。

---

## Phase 3: User Story 1 - 改坏列表页几何时，机器当场拦住 (Priority: P1) 🎯 MVP

**Goal**：090 的「内容区撑满」不变式被钉成机器门禁：短页必须吃满、长页必须按内容撑高，两者都不得写死路径清单。

**Independent Test**：注释掉 `frontend/src/index.css` 里「列表页撑满」的任一入口 → 对应用例**变红**；还原 → 复跑**转绿**。

### Implementation for User Story 1

- [ ] T009 [US1] 创建 `frontend/e2e/geometry-list-page.spec.ts`：文件顶部 `test.use({ viewport: VIEWPORT_WIDE })`（**显式设定 1280×720**，FR-EG-003）；每个用例开头复用 `login(page)`（来自 `frontend/e2e/helpers/login.ts`，FR-EG-004）；定义采样候选常量——短页候选 `/quotas`、`/orders`（ProTable 根）、`/data-retention`、`/departments`（Card 根），长页候选 `/users`、`/customers`；**候选是「候选」不是「判定」**
- [ ] T010 [US1] 在 `frontend/e2e/geometry-list-page.spec.ts` 写**短页桶**用例（FR-EG-005 / FR-EG-006 / FR-EG-012）：逐页 `goto` → `waitForGeometryStable` → 量取 → **运行时判定**该页是短页还是长页（依据滚动盒是否溢出，FR-EG-011，**MUST NOT** 写死「`/quotas` 是短页」）→ 对短页用 `softExpect` 断言 `fill ≈ 0`（±1px）**且** `residual ≈ 0`（±1px）→ 逐页打印实测值 → 末尾 `assertLiveness` **硬断言**「短页 ≥ 2」**且**「至少 1 个 ProTable 根、至少 1 个 Card 根」（两个入口各有样本，缺哪个入口都会红）
- [ ] T011 [US1] 在 `frontend/e2e/geometry-list-page.spec.ts` 写**长页桶**用例（FR-EG-012）：逐页量取 → 断言滚动盒 `scrollHeight > clientHeight`（确实溢出）、`fill ≤ 0`（**不得缩回去**）、**末行完整可见**（不被 `.ant-table{overflow:hidden}` 裁掉）→ **MUST NOT** 对长页断言「填满」→ 末尾 `assertLiveness` **硬断言**「长页 ≥ 1」
- [ ] T012 [US1] 在 `frontend/e2e/geometry-list-page.spec.ts` 补 `docOverflow == 0` 断言（FR-EG-009 的宽屏一侧）：宽屏采样页上文档根不得出现横向滚动
- [ ] T013 [US1] 在 `frontend/e2e/geometry-list-page.spec.ts` 补**桶归属报告**：用例输出里必须能读到「哪几页被判为短页、哪几页被判为长页、判据是什么」（US1 验收场景 4 / SC-EG-008）；**并**确认输出里能读到每类不变式**实际被断言的样本数**
- [ ] T014 [US1] 跑 `cd frontend && pnpm run test:e2e -- geometry-list-page --reporter=list`：确认**全绿**，且逐条核对输出里确实有每页的实测值、桶归属与样本数（**不得**只看「通过」两字——本仓有过「用例绿而它声称的场景根本没执行」的先例）
- [ ] T015 [US1] **定向破坏 ①**（SC-EG-006 / FR-EG-014，[research.md](./research.md) §7）：**临时**注释掉 `frontend/src/index.css` 里「列表页撑满」的**入口 A**（`.page-fade > .ant-pro-table,` 起的那条规则块）→ 跑用例确认**短页 `fill == 0` 在 ProTable 桶上失败、而 Card 桶仍绿** → 留三次运行输出到 `specs/092-geometry-e2e-guard/falsification-evidence.md` → **逐字节还原**（`git diff` 确认生产代码回到原样）
- [ ] T016 [US1] **定向破坏 ②**（同上）：**临时**注释掉 `frontend/src/index.css` 里**入口 B**（`.page-fade > .ant-card:has(.ant-table, .ant-tree, .ant-list)` 那条）→ 跑用例确认**短页 `fill == 0` 在 Card 桶上失败**（与 ① 的失败桶**不同**，这证明报告可定位）→ 留痕 → **逐字节还原** → 复跑**转绿**
      —— ⚠️ ① 与 ② **必须分开做**，不得一次改两处；合并做就看不出「只有对应的那个桶红了」

**Checkpoint**：090 的几何有了会红的机器门禁。

---

## Phase 4: User Story 2 - 窄屏外壳的几何被钉住 (Priority: P2)

**Goal**：091 的窄屏宽度链条与菜单条几何被钉住——回到 24px 的缝或 200×200 的方块，用例当场变红。

**Independent Test**：把 `frontend/src/App.tsx` 的窄屏分支改回去 → 窄屏宽度断言**变红**；还原 → 复跑**转绿**。

### Implementation for User Story 2

- [ ] T017 [P] [US2] 创建 `frontend/e2e/geometry-narrow-shell.spec.ts`：文件顶部 `test.use({ viewport: VIEWPORT_NARROW })`（**显式设定 375×812**，FR-EG-003）；**MUST NOT** 用 `matchMedia` 判定窄屏（spec 边界情形第三条）；每页开头复用 `login(page)`（FR-EG-004）；采样四页 `/stats`、`/customers`、`/orders`、`/roles`（与 091 取证同一组，保持可比）
- [ ] T018 [US2] 在 `frontend/e2e/geometry-narrow-shell.spec.ts` 写**内容区可见宽度**断言（FR-EG-007 前半）：逐页 `waitForGeometryStable` → 量取 → `softExpect(内容区可见宽度 ≈ 视口宽 ± SCROLLBAR_TOLERANCE_PX)`，失败信息带**页面路径 + 「内容区」 + 实测值与应达值**（FR-EG-013）
- [ ] T019 [US2] 在 `frontend/e2e/geometry-narrow-shell.spec.ts` 写**内容容器宽度**断言（FR-EG-007 后半）：`内容容器宽 == 内容区可见宽 − 24`（±1px）
      —— ⚠️ **必须与 T018 分开成两条独立断言**，**MUST NOT** 合并成「两者之差 == 24」。只断言差值会被**缺陷态同时满足**（`0 = 24 − 24`），那是一条**会放过原缺陷的假判据**（见 [checklists/requirements.md](./checklists/requirements.md) 的复核记录与 `specs/091-narrow-shell-collapse/spec.md` 的订正块）
- [ ] T020 [US2] 在 `frontend/e2e/geometry-narrow-shell.spec.ts` 写**菜单容器**断言（FR-EG-008 / research §5）：先把锚点断言钉死——`.ant-menu` 的直接父元素**就是**外壳布局的直接子元素（取法实测同一节点，加这条让「认错对象」不可能，认错时失败信息直接说清而不是给出莫名的小数值）；再断言**宽 = 内容区可见宽度**（＝视口宽，**不是 351**）、**高 = 48**
- [ ] T021 [US2] 在 `frontend/e2e/geometry-narrow-shell.spec.ts` 写**页面级横向溢出**断言（FR-EG-009）：窄屏四页 `docOverflow == 0`；**并**逐页打印四个量的实测值（US2 验收场景 5 / SC-EG-008：人不必读源码就能核对）
- [ ] T022 [US2] 在 `frontend/e2e/geometry-narrow-shell.spec.ts` 末尾 `assertLiveness` **硬断言**「窄屏样本数 ≥ 4」（窄屏判据与数据无关，没有跳过风险，故下限就是采样页数）
- [ ] T023 [US2] 跑 `cd frontend && pnpm run test:e2e -- geometry-narrow-shell --reporter=list`：确认**全绿**，且输出里能逐页读到**内容区可见宽 375 / 内容容器宽 351 / 菜单 375×48 / docOverflow 0** 四个量（对照读数见 [quickstart.md](./quickstart.md) §4）
- [ ] T024 [US2] **定向破坏 ③**（SC-EG-006 / FR-EG-014，091 自己用过的那次破坏，复用以保证可比）：**临时**把 `frontend/src/App.tsx` 的 `{isMobile ? (` 改成 `{false ? (`（窄屏照样渲染侧边栏）→ 跑用例确认「**内容区可见宽 = 视口宽**」失败（退回 **24**）→ 留三次运行输出到 `specs/092-geometry-e2e-guard/falsification-evidence.md` → **逐字节还原** → 复跑**转绿**
      —— 同时记录：破坏期间 `frontend/src/App.render.test.tsx` 里断言 `.ant-layout-has-sider` 存否的两条**结构性单测也应变红**。两边同时红是**好迹象**，不是冲突（但它**不**替代本项——单测量不了宽度，那正是本项存在的理由）

**Checkpoint**：090 与 091 的几何**都有**会红的门禁。

---

## Phase 5: User Story 3 - 护栏对自己的假绿免疫 (Priority: P3)

**Goal**：证明这套用例**真的在断言**，而不是「跑绿了但其实什么都没测」——本仓已经栽过两次的坑。

**Independent Test**：读输出与源码，能指出①每类不变式「至少几个页面真的被断言过」，②定向破坏时红的是**哪一条**。

### Implementation for User Story 3

- [ ] T025 [US3] **活性核对**（SC-EG-005 / FR-EG-010）：**临时**把 `frontend/e2e/geometry-list-page.spec.ts` 里宽屏短页候选**全部换成已知的长页路径**（`/users`、`/customers`）→ 跑用例确认它**红**且失败信息明确说明「**短页样本数为 0**」——**不是静默通过** → **还原** → 复跑转绿 → 留痕
      —— ⚠️ 这一步与 T015/T016 **不同**：那两次破坏的是**生产代码**，这一次破坏的是**候选清单**，验的是「没测到」本身会不会红
- [ ] T026 [US3] **源码审计**（FR-EG-010 / FR-EG-011）：通读 `frontend/e2e/helpers/geometry.ts` 与两个新 spec，确认**不存在** `test.skip()`、`test.fixme()`、`if (条件) { …断言… }`、`expect.soft` 之外的「条件不满足就整段不执行」形态能让断言**整段不执行而套件仍绿**；确认 `.only` / `.skip` 也未出现在任何一处；把审计结论（逐条对照写了什么、在哪一行）记入 `specs/092-geometry-e2e-guard/falsification-evidence.md`
- [ ] T027 [US3] **失败信息可定位性抽查**（FR-EG-013）：从 T015 / T016 / T024 三次破坏的输出里各摘一条失败信息，确认它们能区分是**哪一页**、**哪一个量**（内容区 / 内容容器 / 菜单 / 卡片 / 余量）、**实测值与应达值**分别是什么；**MUST NOT** 是「某个元素宽度不对」这类无法定位的表述。留痕到 `specs/092-geometry-e2e-guard/falsification-evidence.md`
- [ ] T028 [US3] 汇总 `specs/092-geometry-e2e-guard/falsification-evidence.md`：包含**基线**（T002）→ ① ② ③ 三次破坏**各三次运行输出**（破坏后红 / 还原后绿 / 还原后 `git diff` 为空）→ 活性核对（T025）→ 源码审计结论（T026）→ 明确写出**SC-EG-006 的判据**：「只报『新增 X 条用例、全绿』**不构成证据**」
- [ ] T029 [US3] 最终确认**三次破坏全部已还原**：`cd frontend && git status --porcelain` 与 `git diff -- frontend/src/index.css frontend/src/App.tsx` **均为空**（逐字节回到原样），且破坏期间**未提交任何东西**（`git log --oneline -3` 核对）

**Checkpoint**：三个用户故事都可独立验证，且护栏的可信度有留痕支撑。

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T030 四项既有门禁**不得被本项破坏**（SC-EG-007）：`cd frontend && pnpm run typecheck && npx eslint . && pnpm run test && pnpm run build` —— 逐项确认退出码 0，且 `pnpm run test` 的**单测总数不低于 T002 的基线**
- [ ] T031 跑**全量** `cd frontend && pnpm run test:e2e`：确认本项新增用例全绿，且既有 5 个 e2e 文件的通过情况**与 T002 的基线一致**（不新增失败）
- [ ] T032 按 [quickstart.md](./quickstart.md) §2 逐条核对「用例到底测了什么」：每页实测值、短/长页归属、各类样本数、样本数下限是**硬断言**（打印了不算）
- [ ] T033 删除全部临时探针（`frontend/__probe-*.mjs` 等）——它们一律 **untracked、不进仓库**；确认 `git status --porcelain` 里没有探针残留
- [ ] T034 交付登记：在 `specs/README.md` 的模块表与 `specs/roadmap.md` 登记 092（**两处登记**，本仓既有规矩）；**并**在 `specs/090-list-page-fill-height/tasks.md` 里把 T013 回填为「已由 092 落地」并指向 `specs/092-geometry-e2e-guard/`
- [ ] T035 收尾提交：`ListAgents` 确认**无并行会话**在写同一批文件（`frontend/vite.config.ts` 与 `specs/083-engineering-consolidation/data-model.md` 是**别人未提交的改动，全程不得触碰**）；**逐路径 `git add`，禁用 `git add -A` / `git commit -a`**；提交信息遵循 Conventional Commits，并写明三次破坏的留痕位置（`specs/092-geometry-e2e-guard/falsification-evidence.md`）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup（Phase 1）**：无依赖，可立即开始
- **Foundational（Phase 2）**：依赖 Setup —— **阻塞全部用户故事**，`helpers/geometry.ts` 是三个故事的公共输入
- **User Stories（Phase 3+）**：
  - US1（P1）与 US2（P2）都只依赖 Phase 2，**两者互相独立**（不同 spec 文件、不同视口、不同判据）
  - US3（P3）**逻辑上**依赖前两者（它要审计的正是它们的输出与源码），故排在其后
- **Polish（Phase 6）**：依赖全部用户故事完成

### User Story Dependencies

- **US1（P1）**：Phase 2 完成后即可开工，不依赖其它故事 —— **MVP**
- **US2（P2）**：Phase 2 完成后即可开工，**不依赖 US1**（窄屏判据与数据无关，与列表页判据无交集）
- **US3（P3）**：**依赖 US1 与 US2**——它审计的对象（活性计数、定向破坏的留痕、失败信息）全部产生于前两者。这是本项唯一跨故事的依赖，且是**性质上**的（审计必须有被审计物）

### Within Each User Story

- 先在 spec 文件里把 `test.use({ viewport })` 与登录前置定好，再写断言
- **先有会跑的断言，再补活性下限**：T010/T011 的逐页断言先成立，紧随其后的 `assertLiveness` 才有意义
- **定向破坏必须逐个做、逐个还原**：① 与 ② 合并做就看不出「只有对应的那个桶红了」
- 每个故事收尾都必须**复跑转绿**并核对工作区干净

### Parallel Opportunities

- Setup 的 T001 → T002 是**串行**的（T002 的基线要在 T001 核对的前置上取）
- Foundational 的 T003–T008 **全部落在 `frontend/e2e/helpers/geometry.ts` 同一个文件**，故**不可并行**（同文件冲突）
- **US1 与 US2 可并行**：`geometry-list-page.spec.ts` 与 `geometry-narrow-shell.spec.ts` 是两个独立文件，
  Phase 2 完成后可由两人/两个 agent 同时开工（T017 已标 `[P]`）
- **同一次运行不能并行做两次破坏**——破坏是本机对同一份生产代码的临时改动，必须一次一处

---

## Parallel Example: US1 与 US2 同时开工

```bash
# Phase 2（helpers/geometry.ts）完成后，两条线互不干扰：

# 线 A —— User Story 1（宽屏 1280×720）
Task: "在 frontend/e2e/geometry-list-page.spec.ts 写短页桶用例（运行时分类 + soft 断言 + 硬断言活性下限）"
Task: "在 frontend/e2e/geometry-list-page.spec.ts 写长页桶用例（溢出 / fill ≤ 0 / 末行可见）"

# 线 B —— User Story 2（窄屏 375×812）
Task: "在 frontend/e2e/geometry-narrow-shell.spec.ts 写内容区可见宽度断言（独立于内容容器宽）"
Task: "在 frontend/e2e/geometry-narrow-shell.spec.ts 写内容容器宽度断言（独立于内容区可见宽）"

# 注意：两条线都要改 frontend/src/index.css 或 App.tsx 做定向破坏时，必须串行——
# 破坏是临时的，同一时刻只能有一处被改坏。
```

---

## Implementation Strategy

### MVP First（只做 User Story 1）

1. Phase 1：Setup（基线）
2. Phase 2：Foundational（`helpers/geometry.ts`）—— **CRITICAL，阻塞全部故事**
3. Phase 3：User Story 1（列表页几何）
4. **STOP and VALIDATE**：做定向破坏 ① 与 ②，确认**只有对应的桶红**
5. 此时 090 的几何已经有会红的门禁 —— 可独立交付

### Incremental Delivery

1. Setup + Foundational → helper 就绪
2. + US1 → 定向破坏 ①② 留痕 → **090 的门禁成立**
3. + US2 → 定向破坏 ③ 留痕 → **091 的门禁成立**（至此 SC-EG-001~004、006 有据）
4. + US3 → 活性核对 + 源码审计 → **SC-EG-005、008 有据**
5. + Polish → 四项门禁 + 两处登记 + 090/T013 回填 → 交付

### 本项特有的纪律（不得省略）

- **「跑绿」不构成证据**：每个故事的收尾都是「破坏 → 红 → 还原 → 绿」，不是「跑一次通过」
- **三次破坏都必须逐字节还原**：每次还原后 `git diff` 必须为空，破坏期间**不得提交任何东西**
- **两个量分开断言**：窄屏的内容区可见宽与内容容器宽**是两条断言**，不是一条差值断言
- **零样本必须是红的**：`assertLiveness` 里不得有任何「count === 0 就跳过」的分支
- **发现真缺陷另立一项**：若写用例时发现真缺陷，按 spec 非目标第 3 条**另立项**，**不得**在本项里顺手修

---

## Notes

- [P] 任务 = 不同文件、无未完成依赖
- [Story] 标签把任务映射到具体用户故事，便于追溯
- 每个用户故事都应能**独立完成与独立测试**
- 破坏与还原期间**不提交**；提交按逻辑分组（Setup/helper → US1 → US2 → US3 → Polish）
- 避免：笼统任务、同文件冲突、跨故事依赖（本项只有 US3→US1/US2 一条，且是性质上的）
