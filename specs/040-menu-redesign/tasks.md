# Tasks: 菜单分类重设计模块

**Input**: Design documents from `/specs/040-menu-redesign/`

**Prerequisites**: plan.md (required), spec.md (required), research.md

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2

---

## Phase 1: 用户故事 1 - 菜单按业务域分组 (P0)

**Goal**: 8 个业务分组 + 首页，每组 2~5 项。

**Independent Test**: 登录展开各分组，归属合理、数量均衡。

### 实现

- [x] T001 [US1] 重排路由分组数组 in `frontend/src/App.tsx`：新增 marketingRoutes（营销活动/邮件营销/在线表单）、workbenchRoutes（任务/智能建议/数据大屏）；customerRoutes 增流失预警；salesRoutes 增产品；移除 baseRoutes
- [x] T002 [US1] 更新 groupedMenuItems 分组标签为：客户管理/销售管理/交易管理/营销中心/服务协作/工作台/数据分析/系统管理 in `frontend/src/App.tsx`
- [x] T003 [US1] serviceRoutes 移入公告管理与我的审批；adminRoutes 移除这两项 in `frontend/src/App.tsx`
- [x] T004 [US1] dataRoutes 精简为报表/排行/导出 in `frontend/src/App.tsx`

**Checkpoint**: US1 可用——菜单分组均衡清晰

---

## Phase 2: 用户故事 2 - 功能权限归位 (P0)

**Goal**: 公告/审批全员可见；配置类保留 ADMIN。

**Independent Test**: 非 ADMIN 可见服务协作组公告/审批；ADMIN 全量。

### 实现

- [x] T005 [US2] 同步更新 menuKeyOf 映射（公告/审批/流失预警/大屏等 path→key）in `frontend/src/App.tsx`
- [x] T006 [US2] 验证 028 角色过滤与移动端拍平行为不变 in `frontend/src/App.tsx`

**Checkpoint**: US2 可用——权限归位正确

---

## Phase 3: 收尾与验证

- [x] T007 Frontend typecheck / lint / build 通过
- [x] T008 手动验证：管理员 8 组核对、非 ADMIN 可见公告/审批、每组抽查 1 项路由可达
- [x] T009 更新 roadmap 040 标记 `[x]`

**Checkpoint**: 模块完整可用

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (US1)**: 无依赖
- **Phase 2 (US2)**: 依赖 US1 完成（menuKeyOf 随分组移动同步）
- **Phase 3**: 依赖全部完成

## Notes

- 全部改动集中于 `frontend/src/App.tsx`
- 纯前端，无后端/契约/迁移变更
- 提交规范：Conventional Commits
