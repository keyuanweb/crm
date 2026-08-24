# 研究：角色权限设计

## R1 数据模型

**决策**: 3 张表：
- `role`：id, code(唯一), name, description, data_scope(ALL/DEPT/SELF), enabled, built_in, deleted, version, created_at, updated_at
- `role_menu`：id, role_id, menu_key（无 deleted，物理删重建）
- `role_permission`：id, role_id, permission_code

User.role 字符串关联 role.code（保持 JWT/现有逻辑兼容）。

## R2 菜单与权限字典（静态定义）

**决策**: 菜单 key 与权限码在契约文档定义（前端/后端共用常量）：
- 菜单 key：stats, leads, customers, contacts, opportunities, sales-opportunities, quotes, contracts, orders, tasks, products, marketing, tickets, knowledge, exports, at-risk, leaderboard, reports, suggestions, board, users, departments, workflows, sla-policies, custom-fields, contract-templates, audit-logs, recycle-bin
- 权限码：customer:create/update/delete/transfer/import, lead:create/update/delete/convert/assign, opportunity:create/update/delete, order:create/update/delete/payment, contract:create/update/delete/approve, quote:create/update/delete/approve, ticket:create/update/delete/assign/reply, user:manage, role:manage, workflow:manage, report:manage, system:manage

## R3 权限校验

**决策**: `@RequirePermission("customer:delete")` 注解 + AOP 切面：
- 取当前用户 role → 查 role_permission 权限码集合 → 含则放行，否则 403
- ADMIN 内建角色恒有全部权限（兜底）
- 切面只拦截标注了注解的 controller/service 方法（关键写操作）

## R4 fetchMe 扩展

**决策**: AuthService.me 返回 UserInfo + menus + permissions（一次查 role_menu/role_permission）。前端登录后 fetchMe 存入 authStore。

## R5 前端

**决策**:
- App.tsx 菜单分组按 `user.menus` 过滤（分组内无菜单项则整组隐藏）
- 路由守卫：未授权路由（菜单外）跳回 /stats
- `usePermission`/`hasPerm` helper：按钮显隐
- 角色管理页：Table + 编辑 Modal（基本信息 + Tree 勾选菜单 + Checkbox 勾选权限点）
- 用户管理：角色 Select 数据源改 fetchRoles
