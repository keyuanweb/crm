# 契约：角色权限 role-permissions

**Base**: `/api/v1/roles`（ADMIN，`role:manage` 权限）

## GET /roles

角色列表（含菜单/权限码）。

**Response 200**: `{ "items": [ { "id":1, "code":"SALES", "name":"销售", "description":"", "dataScope":"SELF", "enabled":true, "builtIn":false, "menus":["customers",...], "permissions":["customer:create",...] } ], "total": n }`

## POST /roles

创建角色。**Body**: `{ "code":"REGIONAL_MGR", "name":"区域经理", "description":"", "dataScope":"DEPT", "menus":[...], "permissions":[...] }`（code 唯一，重复 409）。

## PUT /roles/{id}

编辑角色（名称/描述/数据范围/启用/菜单/权限；builtIn=ADMIN 菜单权限不可缩减）。

## DELETE /roles/{id}

删除角色（builtIn 拒绝；被用户引用拒绝 409）。

## GET /roles/options

角色下拉选项（启用角色）：`[{ id, code, name, dataScope }]`

## GET /roles/menu-tree

菜单树定义（配置页勾选）：`[{ key:"customers", title:"客户", children:[...] }]`

## GET /roles/permission-defs

操作权限点定义：`[{ code:"customer:create", label:"客户：创建", group:"客户" }]`

## 菜单字典（menuKey）

stats(首页), leads(线索), customers(客户), contacts(联系人), opportunities(商机), sales-opportunities(销售机会), quotes(报价单), contracts(合同), orders(订单), tasks(任务), products(产品), marketing(营销), tickets(客户服务), knowledge(知识库), exports(导出中心), at-risk(流失预警), leaderboard(团队排行), reports(自定义报表), suggestions(智能建议), board(数据大屏), users(用户管理), departments(部门), workflows(工作流), sla-policies(SLA 策略), custom-fields(自定义字段), contract-templates(合同模板), audit-logs(审计日志), recycle-bin(回收站)

## 权限码字典（permissionCode）

- customer:create/update/delete/transfer/import
- lead:create/update/delete/convert/assign
- opportunity:create/update/delete
- order:create/update/delete/payment
- contract:create/update/delete/approve
- quote:create/update/delete/approve
- ticket:create/update/delete/assign/reply
- user:manage / role:manage / workflow:manage / report:manage / system:manage

## GET /auth/me（扩展）

**Response**: `{ "id":1, "username":"admin", "displayName":"系统管理员", "role":"ADMIN", "menus":["stats","customers",...], "permissions":["customer:create",...] }`

## 后端权限校验

`@RequirePermission("customer:delete")` 注解方法：当前用户角色无该权限码 → 403。ADMIN 内建恒放行。

## 备注

- 配置保存 = 删除重建 role_menu/role_permission（事务）。
- 内建 ADMIN：不可删除、菜单全量、权限全量（兜底）。
