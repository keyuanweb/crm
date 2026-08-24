# 数据模型：角色权限

## role

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| code | varchar(50) | 角色编码（唯一），如 ADMIN/SALES/SUPPORT |
| name | varchar(50) | 角色名称 |
| description | varchar(255) | 描述 |
| data_scope | varchar(20) | 默认数据范围：ALL/DEPT/SELF |
| enabled | tinyint | 启用 |
| built_in | tinyint | 内建（1 不可删除） |
| deleted/version/created_at/updated_at | | 审计 |

## role_menu

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| role_id | bigint | 角色 |
| menu_key | varchar(50) | 菜单标识（契约字典） |

## role_permission

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 主键 |
| role_id | bigint | 角色 |
| permission_code | varchar(50) | 操作权限码（契约字典，格式 实体:动作） |

## 关系

- User.role（字符串）→ role.code（关联）
- 角色-菜单 1:N（role_menu）；角色-权限 1:N（role_permission）
- 配置变更 = 删除重建 role_menu/role_permission（事务内）
