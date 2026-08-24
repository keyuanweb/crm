# Tasks: 系统管理子菜单细分模块

**Input**: Design documents from `/specs/041-menu-system-split/`

**Prerequisites**: plan.md (required), spec.md (required), research.md

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1

---

## Phase 1: 用户故事 1 - 系统管理二级分组 (P0)

**Goal**: 系统管理组内 3 个二级子组，移动端拍平。

**Independent Test**: 展开系统管理显示二级子组，归属合理。

### 实现

- [x] T001 [US1] adminRoutes 拆为 3 个子组数组（adminOrgRoutes 组织与权限、adminConfigRoutes 流程与配置、adminAuditRoutes 审计与维护）in `frontend/src/App.tsx`
- [x] T002 [US1] menuRoutes 合并 3 个 admin 子组数组 in `frontend/src/App.tsx`
- [x] T003 [US1] groupedMenuItems 中 g-admin 改为二级嵌套（children 为 3 个 submenu）in `frontend/src/App.tsx`
- [x] T004 [US1] 移动端拍平改为递归函数（兼容二级 children）in `frontend/src/App.tsx`

**Checkpoint**: US1 可用——系统管理二级分组

---

## Phase 2: 收尾与验证

- [x] T005 Frontend typecheck / lint / build 通过
- [x] T006 手动验证：展开系统管理 3 子组、抽查归属、移动端拍平无报错
- [x] T007 更新 roadmap 041 标记 `[x]`

**Checkpoint**: 模块完整可用

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (US1)**: 无依赖
- **Phase 2**: 依赖 US1 完成

## Notes

- 全部改动集中于 `frontend/src/App.tsx`
- 纯前端，无后端/契约/迁移变更
- 提交规范：Conventional Commits
