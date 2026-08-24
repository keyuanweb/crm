# Tasks: 菜单对标成熟 CRM 调整与规划模块

**Input**: Design documents from `/specs/044-menu-crm-alignment/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, 043 差距分析

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2

---

## Phase 1: 用户故事 1 - 菜单名称与分类调整 (P0)

**Goal**: 命名/分组对标成熟 CRM，已实现入口全保留。

**Independent Test**: 登录查看菜单，名称/分组符合方案。

### 实现

- [x] T001 [US1] 调整分组命名与项归属（按 plan 菜单结构表）in `frontend/src/App.tsx`
- [x] T002 [US1] 核对已实现 42 模块入口全部保留 in `frontend/src/App.tsx`

**Checkpoint**: US1 可用——命名与分类对齐

---

## Phase 2: 用户故事 2 - 未实现模块占位 (P0)

**Goal**: 17 个规划模块以 disabled"规划中"占位纳入菜单。

**Independent Test**: 菜单出现占位项，点击不跳转、可识别。

### 实现

- [x] T003 [US2] 路由分组数组扩展 planned 标志支持 in `frontend/src/App.tsx`
- [x] T004 [US2] toItems 渲染占位项（disabled + "规划中"后缀）in `frontend/src/App.tsx`
- [x] T005 [US2] 按 043 方案将占位项归入对应分组 in `frontend/src/App.tsx`

**Checkpoint**: US2 可用——功能地图成形

---

## Phase 3: 收尾与验证

- [x] T006 Frontend typecheck / lint / build 通过
- [x] T007 手动验证：分组命名、占位项点击不跳转、移动端拍平
- [x] T008 更新 roadmap 044 标记 `[x]`

**Checkpoint**: 模块完整可用

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (US1)**: 依赖 043 差距分析（命名依据）
- **Phase 2 (US2)**: 依赖 US1 完成
- **Phase 3**: 依赖全部完成

## Notes

- 全部改动集中于 `frontend/src/App.tsx`
- 占位项不建路由、不加载页面，仅导航规划展示
- 纯前端，无后端/契约/迁移变更
- 提交规范：Conventional Commits
