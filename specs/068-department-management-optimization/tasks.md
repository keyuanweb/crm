# Tasks: 部门管理页面优化

**Input**: Design documents from `/specs/068-department-management-optimization/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: 仅前端单元测试（Vitest），后端不新增测试

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Single project**: `src/`, `tests/` at repository root
- **Web app**: `backend/src/`, `frontend/src/`
- Paths shown below assume single project - adjust based on plan.md structure

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 项目初始化和基础结构

- [ ] T001 检查现有部门管理代码结构（backend/src/main/java/com/crm/controller/DepartmentController.java, backend/src/main/java/com/crm/service/DepartmentService.java, frontend/src/pages/departments/DepartmentListPage.tsx）
- [ ] T002 确认后端 API 兼容性（/api/v1/departments/tree, POST/PUT/DELETE）
- [ ] T003 确认前端类型定义（frontend/src/types/department.ts）

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 核心基础设施 - 必须在用户故事之前完成

**⚠️ CRITICAL**: 无用户故事工作可以在这个阶段完成之前开始

- [ ] T004 扩展 Department 类型定义（frontend/src/types/department.ts）- 添加 description, sortOrder, createdBy, memberCount, childCount 字段
- [ ] T005 扩展后端 DepartmentResponse DTO（backend/src/main/java/com/crm/dto/department/DepartmentResponse.java）- 添加新字段
- [ ] T006 扩展后端 DepartmentRequest DTO（backend/src/main/java/com/crm/dto/department/DepartmentRequest.java）- 添加 description, sortOrder 字段
- [ ] T007 更新后端 DepartmentService.toResponse() 方法（backend/src/main/java/com/crm/service/DepartmentService.java）- 填充新字段
- [ ] T008 更新后端 DepartmentService.buildTree() 方法 - 计算 memberCount 和 childCount
- [ ] T009 添加部门名称唯一性校验（backend/src/main/java/com/crm/service/DepartmentService.java）- create() 和 update() 方法中检查
- [ ] T010 添加部门树深度校验（backend/src/main/java/com/crm/service/DepartmentService.java）- 防止超过 5 层

**Checkpoint**: 基础架构就绪 - 用户故事实现可以现在开始并行

---

## Phase 3: User Story 1 - 部门树形列表与搜索（Priority: P1）🎯 MVP

**Goal**: 实现部门树形展示和关键字搜索功能

**Independent Test**: 可以在已有部门数据的系统中独立验证搜索和树形展示功能

### Implementation for User Story 1

- [ ] T011 [P] [US1] 创建部门搜索组件（frontend/src/components/DepartmentSearch.tsx）- 搜索输入框 + 清除按钮
- [ ] T012 [P] [US1] 创建部门树节点组件（frontend/src/components/DepartmentTreeNode.tsx）- 展开/收起 + 操作按钮
- [ ] T013 [US1] 重构 DepartmentListPage.tsx - 使用 Ant Design Tree 组件替换自定义树形渲染
- [ ] T014 [US1] 实现前端搜索逻辑 - 过滤部门树节点 + 高亮匹配关键字
- [ ] T015 [US1] 添加搜索防抖（300ms）- 避免频繁过滤
- [ ] T016 [US1] 添加加载状态和空状态提示
- [ ] T017 [US1] 添加错误处理和重试机制
- [ ] T018 [P] [US1] 创建单元测试（frontend/src/pages/departments/DepartmentListPage.test.tsx）- 搜索功能测试

**Checkpoint**: 在此点，User Story 1 应该完全可用且可独立测试

---

## Phase 4: User Story 2 - 部门创建与编辑（Priority: P1）

**Goal**: 实现部门创建和编辑功能，支持新增字段

**Independent Test**: 在已有部门数据的前提下可独立完成创建、编辑操作

### Implementation for User Story 2

- [ ] T019 [P] [US2] 扩展创建/编辑表单字段（frontend/src/pages/departments/DepartmentListPage.tsx）- 添加描述、排序号字段
- [ ] T020 [US2] 更新表单验证规则 - 名称必填、描述最大 500 字符
- [ ] T021 [US2] 更新创建/编辑 API 调用 - 传递新字段
- [ ] T022 [US2] 添加表单重置逻辑 - 关闭模态框时重置
- [ ] T023 [US2] 添加表单提交防抖 - 避免重复提交
- [ ] T024 [P] [US2] 创建单元测试（frontend/src/pages/departments/DepartmentListPage.test.tsx）- 创建/编辑功能测试

**Checkpoint**: 在此点，User Stories 1 AND 2 都应该可以独立工作

---

## Phase 5: User Story 3 - 部门删除与防护（Priority: P2）

**Goal**: 实现部门删除功能和安全防护

**Independent Test**: 在已有部门数据的前提下可独立完成删除操作及防护验证

### Implementation for User Story 3

- [ ] T025 [US3] 增强删除确认对话框 - 显示删除风险提示
- [ ] T026 [US3] 处理删除错误响应 - 显示具体错误信息（有子部门/有成员）
- [ ] T027 [US3] 删除成功后刷新树形列表
- [ ] T028 [US3] 添加删除操作日志（前端 console.log）
- [ ] T029 [P] [US3] 创建单元测试（frontend/src/pages/departments/DepartmentListPage.test.tsx）- 删除功能测试

**Checkpoint**: 在此点，User Stories 1, 2, AND 3 都应该可以独立工作

---

## Phase 6: User Story 4 - 部门详情查看（Priority: P2）

**Goal**: 实现部门详情查看功能

**Independent Test**: 在已有部门数据的前提下可独立验证详情查看功能

### Implementation for User Story 4

- [ ] T030 [P] [US4] 创建部门详情组件（frontend/src/components/DepartmentDetail.tsx）- 展示部门完整信息
- [ ] T031 [US4] 在 DepartmentListPage.tsx 中添加详情查看按钮
- [ ] T032 [US4] 实现详情模态框/抽屉 - 桌面端 Modal，移动端 Drawer
- [ ] T033 [US4] 添加详情加载状态
- [ ] T034 [US4] 添加详情错误处理
- [ ] T035 [P] [US4] 创建单元测试（frontend/src/components/DepartmentDetail.test.tsx）- 详情组件测试

**Checkpoint**: 所有用户故事现在应该可以独立工作

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: 影响多个用户故事的改进

- [ ] T036 [P] 响应式布局优化 - 移动端适配（frontend/src/pages/departments/DepartmentListPage.tsx）
- [ ] T037 [P] 无障碍访问优化 - ARIA 标签 + 键盘导航
- [ ] T038 代码清理和重构 - 提取公共组件和工具函数
- [ ] T039 性能优化 - 虚拟滚动（如果部门数量>100）
- [ ] T040 [P] 国际化支持 - 添加 i18n 翻译键（frontend/src/i18n/zh-CN.ts, frontend/src/i18n/en.ts）
- [ ] T041 运行 quickstart.md 验证指南 - 手动冒烟测试
- [ ] T042 更新 README 或文档（如有）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖 - 可以立即开始
- **Foundational (Phase 2)**: 依赖 Setup 完成 - 阻塞所有用户故事
- **用户故事 (Phase 3+)**: 都依赖 Foundational 阶段完成
  - 用户故事可以并行进行（如果有团队）
  - 或按优先级顺序进行（P1 → P2 → P3）
- **Polish (最终阶段)**: 依赖所有期望的用户故事完成

### User Story Dependencies

- **User Story 1 (P1)**: Foundational (Phase 2) 后可以开始 - 无其他故事依赖
- **User Story 2 (P2)**: Foundational (Phase 2) 后可以开始 - 可能与 US1 集成但应可独立测试
- **User Story 3 (P3)**: Foundational (Phase 2) 后可以开始 - 可能与 US1/US2 集成但应可独立测试
- **User Story 4 (P4)**: Foundational (Phase 2) 后可以开始 - 可能与 US1/US2/US3 集成但应可独立测试

### Within Each User Story

- 模型优先于服务
- 服务优先于端点
- 核心实现优先于集成
- 故事完成后再移动到下一个优先级

### Parallel Opportunities

- 所有 Foundational 任务标记 [P] 可以并行运行
- Foundational 阶段完成后，所有用户故事可以并行开始（如果有团队容量）
- 不同用户故事可以由不同的团队成员并行处理

---

## Parallel Example: Foundational Phase

```bash
# Launch all foundational tasks together:
Task: "扩展 Department 类型定义（frontend/src/types/department.ts）"
Task: "扩展后端 DepartmentResponse DTO（backend/src/main/java/com/crm/dto/department/DepartmentResponse.java）"
Task: "扩展后端 DepartmentRequest DTO（backend/src/main/java/com/crm/dto/department/DepartmentRequest.java）"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1: Setup
2. 完成 Phase 2: Foundational（关键 - 阻塞所有故事）
3. 完成 Phase 3: User Story 1
4. **停止并验证**: 独立测试 User Story 1
5. 部署/演示（如准备好）

### Incremental Delivery

1. 完成 Setup + Foundational → 基础就绪
2. 添加 User Story 1 → 独立测试 → 部署/演示（MVP！）
3. 添加 User Story 2 → 独立测试 → 部署/演示
4. 添加 User Story 3 → 独立测试 → 部署/演示
5. 添加 User Story 4 → 独立测试 → 部署/演示
6. 每个故事添加价值而不破坏之前的故事

### Parallel Team Strategy

有多个开发者时：

1. 团队一起完成 Setup + Foundational
2. Foundational 完成后：
   - 开发者 A: User Story 1
   - 开发者 B: User Story 2
   - 开发者 C: User Story 3
   - 开发者 D: User Story 4
3. 故事独立完成和集成

---

## Notes

- [P] tasks = 不同文件，无依赖
- [Story] label 将任务映射到特定用户故事以实现可追溯性
- 每个用户故事应该可以独立完成和测试
- 验证测试在实现之前失败
- 每次完成任务或逻辑组后提交
- 在任何一个检查点停止以独立验证故事
- 避免：模糊的任务、相同文件冲突、打破独立性的跨故事依赖

---

## 订正（2026-09-15 登记清扫）——「0/42」这个数该怎么读

`specs/roadmap.md` 此前把本项与 `067` 并列记为「**代码确实在**……做了没回填勾选」，并写明**回填有意未做**、
理由是「回填须**逐条核对**实现与任务的对应，把『看着像做了』当成『确实做了』正是本项目反复踩的假绿形态」。
**那次被推迟的逐条核对，2026-09-15 做了**（只读，口径=生产代码里的字符串锚点）。
结果：**该谨慎是对的**——**对 `067` 大体成立，对本项偏乐观，须订正。**

本文件 42 条**一条都没勾**，实测分布：**20 条有落点 / 6 条部分 / 16 条无落点**。
而「无落点」里混着**三种性质完全不同**的东西，**不能一律读成「没做」**。原文与勾选行**一律保留、不回写**。

### 一、先看这一节：判定的分层

| 层 | 含义 | 本项的实测 |
|---|---|---|
| **规格层（FR/SC）** | `spec.md` 的 10 条 FR + 4 条 SC | **有 1 条 FR 无落点**（见三） |
| **plan/tasks 层** | 组件拆分、测试文件、无障碍、移动端分支等 | 未落地的主要在这一层（见二） |

**决定性质的关键事实**：`spec.md` 里 **`高亮` / `防抖` / `空状态` / `ARIA` / `抽屉` / `Drawer` / `虚拟滚动` 全部 0 命中**
⇒ 二(a) 那些条目**不是规格级要求**，是 plan/tasks 层的实现细节与增强条款。

### 二、无落点的 16 条，按性质分三类

**(a) 真缺口——明确了产物/行为，树里没有（10 条）**

| ID | 要求 | 实测 |
|---|---|---|
| T014 | 过滤 + **高亮匹配关键字** | 过滤有（`filteredTreeData`）；**高亮无落点**（无 mark/highlight） |
| T015 | 搜索**防抖 300ms** | 无 debounce / `setTimeout`（唯一 300 命中是 `maxWidth: 300`，边界已自证） |
| T016 | 加载态**与空状态** | `loading={loading}` 有；**空状态无落点**（无 `Empty`） |
| T018 / T024 / T029 | 搜索 / 创建编辑 / 删除的单元测试 | `frontend/src/pages/departments/` 下**只有 `DepartmentListPage.perm.test.tsx`**；`DepartmentListPage.test.tsx` **不存在** |
| T025 | 删除**风险提示** | 有 `Popconfirm` 与「确定删除部门「{{name}}」吗？」，**无风险提示** |
| T032 | 桌面 Modal + **移动端 Drawer** | Modal 有；**未引入 `Drawer`**，无移动端分支 |
| T033 / T034 | 详情**加载态** / **错误处理** | 均无落点（详情直接复用树里已加载的行数据） |
| T035 | 详情组件测试 `DepartmentDetail.test.tsx` | 文件不存在 |
| T037 | **ARIA 标签 + 键盘导航** | 页面内 `aria-` **0 命中** |

**(b) 形态不同——能力在，但没按任务要求「拆」出来（3 条）**
T011（`components/DepartmentSearch.tsx`：搜索框**内联**在页面）、T012（`components/DepartmentTreeNode.tsx`：`renderTreeNode` **内联**在 `:191`）、
T030（`components/DepartmentDetail.tsx`：详情**内联**在 `:290-339`）。⇒ 差的是**抽取与复用形态**，不是功能。

**(c) 不是缺口（3 条，理由各不相同）**
- **T028「添加删除操作日志（前端 `console.log`）」**：**加了才是退步** ⇒ 未加 = **正确状态**；建议**撤销该任务**，而不是完成它。
- **T039「虚拟滚动（如果部门数量>100）」**：原文自带条件，条件未触发 ⇒ 不构成缺口。
- T041（`quickstart.md` 手动冒烟）、T042（「更新 README **如有**」）：一个无产物可锚、一个是「如有」的弱条款；本次**未执行/未判定**。

### 三、⚠️ 规格层：**FR-003（分页）无落点，且与 research 的决策相反**

- **FR-003 原文**（`spec.md:90`）：「系统**必须**支持**分页**显示部门列表（**默认 20 条/页**）」。
- **实测**：`frontend/src/pages/departments/` 内 `pagination` / `Pagination` / `pageSize` / `loadData` **全部 0 命中**；
  `<Tree>` 全量渲染（`expandedKeys` + `showLine`）⇒ **无任何分页/懒加载落点**。
- **决策文档怎么说**（这一步不能省）：`research.md` §4 的决策是「**前端分页（树形节点展开后分页）**」，
  其「已知问题 1」亦承认「树形结构分页复杂，展开/收起状态难以保持」。
- **⇒ 性质**：这**不是** plan 层细节，而是**一条规格级 MUST 与实现不符**，且**决策文档站在实现的反面**；
  `spec.md` 的 FR-003 **既未订正也未删除**。**须由用户裁决**：① 补做前端分页；**或** ② 按决策现状订正 FR-003
  （改为「树形全量展示，不分页」并写明理由与影响）。**两条路都必须留痕，不得静默。**

### 四、本项 `✅` 的读法

本项**核心可用性成立**：树的展示与展开收起、按名称过滤、创建/编辑（含 `description` / `sortOrder`）、
删除防护（`validateNameUnique` / `validateDepth` / 子部门与成员拦截链）、详情查看、双语 i18n 40 键对齐
——均已在生产代码里指到，故**不改变「已交付」这一步的判断**。
但要**如实记**：**规格层有 FR-003 未落地**（见三），**plan 层有 10 条真缺口**（见二(a)）。
上述缺口**不在本次登记清扫的范围内**（本次只做文档），列在此处**作为另立一项的输入**。
