# Tasks: 067/068 真缺口收口（部门页 UX 八条 + 使用地图警示用例）

**Input**: Design documents from `/specs/095-ux-gap-closeout/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md

**Tests**: 本项**含**测试交付（两处新测试文件 + 一处用例追加），但**不是** TDD 顺序——**如实记**：实现先写、用例后补，护栏的「有牙齿」由 **T018 的定向破坏留痕**证明。**不得**据此声称走过 spec-first（见 `plan.md` 的 Constitution Check 原则四）。

**Organization**: 按 spec.md 的三个用户故事分相；订正类任务集中在最后一相。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无依赖）
- **[Story]**: 该任务属于哪个用户故事

## Path Conventions

- 前端：`frontend/src/`；工件：`specs/095-ux-gap-closeout/`

---

## Phase 1: Setup（立项与共用件）

- [ ] T001 立项：写 `specs/095-ux-gap-closeout/` 的 `spec.md` / `plan.md` / `research.md` / `tasks.md` / `quickstart.md` / `checklists/requirements.md` / `falsification-evidence.md`（**手工写，不跑 `/speckit-*`**——`.specify/feature.json` 是共享单槽指针，本项全程不碰）
- [ ] T002 两处登记：`specs/README.md` 的模块表加 `095` 行；`specs/roadmap.md` 的 `## 当前进度` 末尾追加 `095` 行
- [ ] T003 [P] 提取高亮件 → 新建 `frontend/src/components/ui/Highlight.tsx`（照抄 `pages/search/SearchResultPage.tsx` 的页内私有实现，补 `export default` 与 props 类型），在 `frontend/src/components/ui/index.ts` 加 barrel 出口，并改 `SearchResultPage.tsx` 从 barrel 引入、删掉页内定义（**R7 靠 barrel 满足**）
- [ ] T004 [P] 新增 `frontend/src/hooks/useDebouncedValue.ts`（`useDebouncedValue<T>(value, delay = 300)`，卸载时清定时器）+ 单测 `frontend/src/hooks/useDebouncedValue.test.ts`（`vi.useFakeTimers()` + `renderHook`；**该 hook 是新函数，无单测会拉低 `functions` 覆盖率**）

**Checkpoint**：两只共用件就位，其余批次无阻塞。

---

## Phase 2: User Story 1 - 部门树检索体验（P1）

**Goal**: 搜索时看得见命中位置、不再逐字符重算、搜不到有明确空态、展开态不再自相矛盾。

**Independent Test**: 在 `DepartmentListPage.test.tsx` 的「搜索」组内独立验证（过滤 + 高亮 + 防抖最终一致 + 空态 + 展开键）。

- [ ] T005 [US1] `frontend/src/pages/departments/DepartmentListPage.tsx`：接防抖——`const q = useDebouncedValue(searchValue, 300)`；`filteredTreeData` 的依赖与「自动展开匹配搜索的节点」effect 的依赖**一并**改挂 `q`（锚：`}, [treeData, searchValue])` 与 `}, [searchValue, treeData])`）
- [ ] T006 [US1] 同文件：高亮——`renderTreeNode` 里 `{node.name}` 换成 `<Highlight text={node.name} keyword={q} />`（锚：`{(node.childCount ?? 0) > 0 ? '📁' : '📄'} {node.name}`）
- [ ] T007 [US1] 同文件：空状态——`displayTreeData.length === 0` 时渲染 `<PageState state="empty" />` 取代 `<Tree>` 的空渲染；**不动** `<Card loading={loading}>`（两层占位会打架，见 `PageState.tsx` 文件头禁令）
- [ ] T008 [US1] 同文件：修正展开态自相矛盾——该 effect 的 `else` 分支由 `setExpandedKeys([])` 改为恢复 `load()` 算出的「全部展开」键。**这是有意的可见行为变化**，写进提交信息

**Checkpoint**：`pnpm typecheck` 通过，页面可渲染（用例在 Phase 5 补）。

---

## Phase 3: User Story 2 - 详情与删除的风险可见（P1）

**Goal**: 详情由独立组件渲染、按断点二择形态、删除给出基于真实数字的风险提示、搜索框与树有可朗读名称。

**Independent Test**: `DepartmentDetail.test.tsx` 独立验证组件九项字段；页面两分支在 `DepartmentListPage.test.tsx` 的「详情」组内验证。

- [ ] T009 [US2] 新建 `frontend/src/components/DepartmentDetail.tsx`：**纯展示组件**（props 只有 `department: Department`），把 `DepartmentListPage.tsx` 现内联的九项字段（`detailName/Id/Parent/TopLevel/Desc/Sort/CreatedAt/CreatedBy/Members/Children`）整体搬入。**维持**逐字段 `<div>` 形态，**不**引入 `Descriptions`（会撞门禁 R8）
- [ ] T010 [US2] 同页面：删掉内联详情体、改 `import DepartmentDetail from '../../components/DepartmentDetail'` 并使用（**R7 要求它被产品文件引用**）
- [ ] T011 [US2] 同页面：详情容器按断点二择——照抄 `pages/map/UsageMapPage.tsx` 的范式（`Grid.useBreakpoint()` + `isMobile = !screens.lg`），移动端 `Drawer`（`placement="bottom"`、`height="auto"`）、桌面端 `Modal`（`width={600}`、`footer={null}`、**`destroyOnClose`**——antd 5.22.0 的正确拼写），两个容器**都渲染**、用 `open={detailOpen && isMobile}` / `open={detailOpen && !isMobile}` 二择
- [ ] T012 [US2] 同页面：删除确认加风险提示——`Popconfirm` 的 `description` 用该节点自身的 `childCount`/`memberCount` 现算（锚：`<Popconfirm title={t('pages.departmentList.confirmDelete', { name: node.name })}`），文案须讲明「**有子部门或成员时删除会被拒绝**」（后端 `DEPARTMENT_HAS_CHILDREN_OR_MEMBERS` 真会拦），**不得**写成「不可恢复」这类在本系统不成立的泛化风险
- [ ] T013 [US2] 同页面：无障碍——搜索框与树容器加 `aria-label={t(…)}`（**必须经 `t()`**：门禁 R6 的裸字面量正则 + 冻结台账不得增）。键盘导航的交付**限于结构前提**（`role="tree"`/`treeitem` 在场、节点可聚焦），**不声称验证了方向键行为**
- [ ] T014 [US2] `frontend/src/i18n/zh-CN.ts` 与 `frontend/src/i18n/en.ts`：新增键（`confirmDeleteRisk` + 每个 `aria-label` 一键），**双语同一次提交**（`i18n:check` 双向比对 + `setup.ts` 缺键抛错双重兜底）

**Checkpoint**：`pnpm typecheck && pnpm i18n:check && pnpm ui:check` 三道绿。

---

## Phase 4: User Story 3 - 使用地图警示可回归（067 的 T009）

- [ ] T015 [US3] `frontend/src/pages/map/UsageMapPage.test.tsx` 追加「状态流转异常状态红色警示」用例：`await waitFor(() => expect(graphConstructor).toHaveBeenCalled())` 后取 `graphConstructor.mock.calls.at(-1)?.[0]`，调用其 `node.style.stroke({ data: { warning: true, color: '#8c8c8c' } })` 断言 `'#cf1322'`、`node.style.lineWidth({ data: { warning: true } })` 断言 `3`，并以**非 warning 节点做同用例内的对照**（返回原色 / `2`）防判据恒真。**不改生产代码**

**Checkpoint**：`pnpm test UsageMapPage` 绿。

---

## Phase 5: 测试（T018 / T024 / T029 / T035）

- [ ] T016 新建 `frontend/src/pages/departments/DepartmentListPage.test.tsx`（**一个文件承载三条任务**：T018 搜索组 / T024 创建编辑组 / T029 删除组）。骨架抄 `DepartmentListPage.perm.test.tsx`（`renderWithProviders` + `vi.mock('../../services/departmentService')` 四个函数 + `useAuthStore.setState` + `vi.clearAllMocks()` + `localStorage.clear()`）。覆盖：过滤、`<mark>` 高亮、防抖后最终一致、`page-state-empty` 在场、删除风险文案、`aria-label` 在场、**详情两个分支各一条独立断言**（桌面分支须**在本文件内覆盖 `matchMedia`**，范式见 `UsageMapPage.test.tsx:37-50`，并留注释「全局桩未改」）。**不新增 service 函数** ⇒ `perm.test.tsx` 的 mock 不动
- [ ] T017 新建 `frontend/src/components/DepartmentDetail.test.tsx`：九项字段逐一断言 + 无上级部门时显示「顶级」+ 空值回落 `-`
- [ ] T018 **定向破坏 5 处 + 留痕**（逐条做、逐条**逐字节还原**、破坏期间**不提交**；逐字输出写进 `falsification-evidence.md` 的 §B–§F）：① 高亮换回裸 `{node.name}` → 高亮断言红；② 去掉防抖直连 `searchValue` → 防抖断言红；③ 删 `<PageState state="empty" />` → 空态断言红；④ `isMobile` 写死 `true` → 桌面断言红；⑤ 删搜索框 `aria-label` → aria 断言红

**Checkpoint**：`pnpm test` 全绿；五处破坏各自被观测到转红并已还原。

---

## Phase 6: 订正与收口

- [ ] T019 `specs/068-department-management-optimization/spec.md`：**FR-003 的订正覆盖三处**——① `FR-003`（「必须支持分页…默认 20 条/页」）；② **US1 验收场景 2**（「部门列表超过一页…切换页码」）；③ `research.md` §4 的「决策：前端分页（树形节点展开后分页）」及「已知问题 1」。原文一律保留 + 带日期 ⚠️ 订正块；写明改「树形全量展示、不分页」，理由取 Assumptions（全量加载、1000 部门以内）与 research §4 自认的「展开/收起难以保持」；**并写明不违章程原则五**（该条射程是**可分页的列表端点**，部门树是**树形全量端点**）；**性质须写明是「事后按现状订正」**（照 090 的先例），**不是**「决策时就不做」
- [ ] T020 `specs/068-department-management-optimization/tasks.md`：追加 ⚠️ 订正块——T033/T034 记为「**结构性不适用**」（详情复用列表已加载的同引用、无网络窗口；后端无 `GET /departments/{id}` 且 plan 明文「无新增后端 API」），并说明 **T030 因 T035 无被测对象而破例纳入**。**原文与勾选行一律保留、不回写**
- [ ] T021 `specs/067-usage-map-ux/tasks.md`：追加 ⚠️ 订正块——T001 从「真缺口清单」移出（**非缺口**：三个样式字段全仓无赋值、无读取方；spec FR-001 由页面级统一默认值满足），真缺口**收敛为 1 条**（T009，本批已实现）。**不回写勾选行**
- [ ] T022 `frontend/src/pages/departments/DepartmentListPage.tsx`：**仅订正注释**（原文保留 + 日期 ⚠️），写明「`t` 被遮蔽」是假阳性、「`walk` 未防 `children` 缺失」的触发条件在后端 `non_null` + DTO 初始化为 `ArrayList` 下不成立。**代码一行不改**
- [ ] T023 `specs/roadmap.md`：在既有「订正（2026-09-15 登记清扫）」块**之后追加**一段（**不改旧行**）：067 真缺口 2→1、068 的 10 条 (a) 的最终归属（8 实现 / 2 订正）、095 已消化该批
- [ ] T024 跑七道门禁全量并勾选本文件：`cd frontend && pnpm typecheck && pnpm lint && pnpm i18n:check && pnpm menu:check && pnpm perms:check && pnpm ui:check && pnpm test:coverage`

---

## 验证

- [ ] V1 七道前端门禁退出码全 0；测试文件数 **83 → 85**
- [ ] V2 覆盖率四项 ≥ **33.6 / 47.2 / 21.4 / 33.6**，**阈值未改动**
- [ ] V3 `ui:check` 的**白名单冻结读数不增**
- [ ] V4 五处定向破坏逐条留痕，还原后生产代码**逐字节**回原样
- [ ] V5 六处订正逐处可查、原文未删
- [ ] V6 手工冒烟（`quickstart.md`）：搜索高亮与 300ms 延迟、清空后树展开全部、搜索无结果的空态、删除弹窗的真实数字、窄窗口详情走底部抽屉
- [ ] V7 **可见变化如实披露**（展开态、窄屏抽屉），**未获视觉背书**，记为欠账

## 明确不做

见 `spec.md` 的「非目标」（分页实现、后端改动、T011/T012、T028、T039、T041/T042、067 的 T006 红图标、`FormModal`、`Descriptions`、e2e 几何、历史勾选回填、003 的真实 MySQL 验证）。
