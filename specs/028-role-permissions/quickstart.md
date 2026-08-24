# 快速开始：角色权限

## 后端

1. Flyway V46：role/role_menu/role_permission 表 + seed（ADMIN/SALES/SUPPORT 内建角色及菜单/权限）。
2. 实体/Mapper：Role/RoleMenu/RolePermission。
3. `RoleService`：CRUD + 配置（删除重建关联）+ options/menu-tree/permission-defs。
4. `RoleController`：/api/v1/roles 端点。
5. `@RequirePermission` + 切面：关键写操作校验。
6. `AuthService.me` 返回 menus+permissions。
7. 测试：RoleServiceTest + RoleIT。

## 前端

1. `types/role.ts` + `services/roleService.ts`。
2. `RoleListPage`：角色列表 + 编辑弹窗（基本信息 + 菜单 Tree + 权限 Checkbox）。
3. App.tsx：菜单按 user.menus 过滤 + 未授权路由跳回 /stats。
4. authStore：UserInfo + menus/permissions；`hasPerm` helper。
5. 关键页面按钮显隐（客户/订单/用户等）。
6. 用户管理：角色下拉改 fetchRoles。

## 验证

- 后端：`mvn test`（新增 Role 测试，不影响既有 252）。
- 前端：`pnpm run typecheck` + `lint` + `test`。
- 手动：创建角色→配菜单/权限→建用户关联→登录验证菜单过滤与按钮显隐→直调 API 403。
