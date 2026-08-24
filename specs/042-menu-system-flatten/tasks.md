# Tasks: 系统管理菜单提升与重组模块

**Input**: Design documents from `/specs/042-menu-system-flatten/`

**Prerequisites**: plan.md (required), spec.md (required), research.md

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1

---

## Phase 1: 用户故事 1 - 系统管理子组提升为一级 (P0)

**Goal**: 三个一级分组（系统管理/流程与配置/审计与维护），无二级嵌套。

**Independent Test**: 登录后菜单直接显示三个一级分组，展开后归属正确。

### 实现

- [x] T001 [US1] groupedMenuItems 中 g-admin 拆为三个一级分组：g-admin（系统管理，原组织与权限更名）、g-config（流程与配置）、g-audit（审计与维护）in `frontend/src/App.tsx`
- [x] T002 [US1] openKeys 自动展开逻辑按新分组 key 更新（g-admin/g-config/g-audit 对应路径）in `frontend/src/App.tsx`
- [x] T003 [US1] 移动端递归拍平保持（flattenMenuItems 不变）in `frontend/src/App.tsx`

**Checkpoint**: US1 可用——系统管理扁平化

---

## Phase 2: 收尾与验证

- [x] T004 Frontend typecheck / lint / build 通过
- [x] T005 手动验证：三个一级分组、抽查归属、进入配置项自动展开、移动端拍平
- [x] T006 更新 roadmap 042 标记 `[x]`

**Checkpoint**: 模块完整可用

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (US1)**: 依赖 041 完成（在二级嵌套基础上扁平化）
- **Phase 2**: 依赖 US1 完成

## Notes

- 全部改动集中于 `frontend/src/App.tsx`
- 纯前端，无后端/契约/迁移变更
- 提交规范：Conventional Commits
