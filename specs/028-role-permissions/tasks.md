# Tasks: 角色权限管理

**Input**: Design documents from `/specs/028-role-permissions/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/role-permissions.md

**Tests**: 章程原则四要求测试先于实现（红→绿），本功能含后端单元/集成 + 前端测试。

## Phase 1: 基础设施（数据库 + 字典）

- [x] T001 [P] [US1] 后端：新增 `db/migration/V46__role_permissions.sql`——role/role_menu/role_permission 三表 + seed（ADMIN 全菜单全权限 ALL、SALES 业务菜单+业务操作 SELF、SUPPORT 客服菜单+客服操作 DEPT，built_in=1）。
- [x] T002 [P] [US1] 后端：实体 Role/RoleMenu/RolePermission + 3 个 Mapper。
- [x] T003 [P] [US1] 后端：常量类 `RoleConstants`（菜单字典、权限码字典的 key/中文 label 分组），与契约一致。

## Phase 2: 后端测试先行（TDD 红）

- [x] T004 [P] [US1] 后端：编写 `backend/src/test/java/com/crm/service/RoleServiceTest.java` 单元测试——覆盖：角色 CRUD、菜单/权限配置保存（删除重建）、内建角色删除拒绝、被引用删除拒绝、options/menu-tree/permission-defs。此时 RoleService 未实现，测试失败（红）。
- [x] T005 [P] [US1] 后端：编写 `backend/src/test/java/com/crm/integration/RoleIT.java` 集成测试——创建角色→配置菜单/权限→me 返回 menus/permissions→无权限用户调删除 API 403。此时接口未实现，测试失败（红）。

## Phase 3: 后端实现

- [x] T006 [US1] 后端：新增 `dto/role/RoleRequest.java`、`RoleResponse.java`（+builtIn）、`RoleOption.java`。
- [x] T007 [US1] 后端：新增 `service/RoleService.java`——CRUD（code 唯一校验、builtIn 删除拒绝、被用户引用删除拒绝）+ configure（事务删除重建）+ options/menuTree/permissionDefs + permissionsOf/menusOf（供切面/me）。（依赖 T002/T003/T006）
- [x] T008 [US1] 后端：新增 `controller/RoleController.java`——GET /roles、POST /roles、PUT /roles/{id}、DELETE /roles/{id}、GET /roles/options、GET /roles/menu-tree、GET /roles/permission-defs。（依赖 T007）
- [x] T009 [US1] 后端：新增 `security/RequirePermission.java` 注解 + `security/PermissionAspect.java` 切面——取当前用户 role → RoleService.permissionsOf 校验（ADMIN 恒放行），无权限抛 403。（依赖 T007）
- [x] T010 [US1] 后端：`AuthService.me` 扩展返回 menus+permissions（UserInfo 加字段，ADMIN 全量兜底）。（依赖 T007/T009）

## Phase 4: 后端权限点标注（关键写操作）

- [x] T011 [US2] 后端：为关键写操作加 `@RequirePermission`——CustomerService.delete、UserService create/update/resetPassword、RoleController 全部；`UserService` 角色校验改查角色表（替代硬编码 ADMIN/SALES/SUPPORT，支持自定义角色）；`UserCreateRequest.role` Pattern 改通用编码（角色存在性由 service 校验）。（依赖 T009）

## Phase 5: 前端

- [x] T012 [P] [US1] 前端：`types/role.ts` + `services/roleService.ts`。
- [x] T013 [US1] 前端：新增 `pages/roles/RoleListPage.tsx`——角色列表 + 编辑弹窗（基本信息表单 + 菜单 Tree 勾选 + 权限 Checkbox 分组）；`App.tsx` 注册路由（系统管理分组）。（依赖 T012）
- [x] T014 [US1] 前端：`authStore.ts` UserInfo 加 menus/permissions；`App.tsx` 菜单分组按 user.menus 过滤（path→menuKey 映射处理多段路径）+ 未授权路由守卫。（依赖 T013 类型）
- [x] T015 [US2] 前端：新增 `hooks/usePermission.ts`（hasPerm，ADMIN 恒真）；CustomerListPage 按钮显隐（新增/删除/导入/转移/编辑）。（依赖 T014）
- [x] T016 [P] [US1] 前端：`UserManagementPage.tsx` 角色下拉改 fetchRoles 数据源（动态加载启用角色）。

## Phase 6: 验证与收尾

- [x] T017 后端：`mvn test` 全量通过（260 tests：新增 RoleServiceTest 8 + RoleIT 4；UserServiceTest/AuthServiceTest/UserContractTest 适配 RoleService 注入与角色校验）。
- [x] T018 前端：`pnpm run typecheck` + `lint` + `test` 全量通过（32 tests）。
- [x] T019 [P] 手动冒烟：创建 READONLY 角色（仅 stats/customers 菜单、0 权限）→ 建用户关联 → me 返回 menus=["stats","customers"]/perms=[] → 删除客户 API 403 → 角色管理 API 403 → 前端菜单仅显示首页/客户、无新增/导入按钮。
- [x] T020 [US1] 前端：优化编辑角色页——① 修复 Bug：菜单 Tree 全选分组时父节点 key（__group_ 前缀）被存入 checkedMenus 导致后端存非法 menu_key（onCheck 过滤叶子 key）；② 内建角色显示 Alert 提示（ADMIN 菜单/权限全量不可缩减）；③ 菜单/权限分区卡片化（灰底圆角容器）+ 实时计数（已选 X/28、已选 X 个权限点）+ 全选/清空/重置快捷按钮；④ 权限每组头部全选 Checkbox（支持半选态）；⑤ enabled 改 Switch；⑥ 弹窗 760→800 + 内容限高滚动（maxHeight 68vh）；⑦ 字段加 extra 说明；⑧ 按用户反馈二次优化：可见菜单与数据操作权限改 **Tabs 切换**（tab 标题带计数，减少弹窗纵向占用）；可见菜单弃用 Tree 改**分组纵向 + 叶子横向排列**（组头全选/半选 + 组内 flex wrap 横排）；数据操作权限改**网格对齐（Row/Col 3 列）+ 组内横向 wrap**；⑨ 按用户反馈三次优化：移除 Modal body 的 maxHeight 68vh/overflowY auto 与菜单 Tab 内 maxHeight 300 限高（弹窗与 Tab 内容自适应完整显示），实测弹窗 927px < 1000 视口、body overflow visible、全弹窗无可滚动元素（仅 antd Tabs 导航溢出折叠按钮）；typecheck/lint/32 tests 通过。

## Dependencies & Execution Order

- T001/T002/T003 可并行（基础设施）。
- T004/T005 可并行，均为红阶段；依赖 T001-T003。
- T006 依赖 T002/T003；T007 依赖 T006；T008/T009/T010 依赖 T007。
- T011 依赖 T009。
- T012 无依赖；T013 依赖 T012；T014 依赖 T013 类型；T015/T016 依赖 T014。
- Phase 6 在所有实现完成后执行。

## Notes

- 菜单 key 与权限码字典与契约一致（RoleConstants 单源）。
- 内建角色：builtIn=1 不可删；ADMIN 菜单/权限全量兜底。
- 配置保存 = 删除重建关联（事务）。
- JWT 不放权限；fetchMe 返回。
