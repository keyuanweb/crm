# Implementation Plan: 角色权限管理

**Branch**: `028-role-permissions` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

## Summary

新增角色权限体系：`role`/`role_menu`/`role_permission` 三表 + Flyway V46 seed（ADMIN/SALES/SUPPORT 内建角色）；`RoleController`/`RoleService`（CRUD + 菜单/权限配置 + 菜单树/权限点字典）；`GET /auth/me` 扩展返回 menus+permissions；后端关键写操作权限校验（@RequirePermission 注解 + 切面）；前端角色管理页（系统管理分组）+ 菜单动态过滤 + 操作按钮按权限显隐 + 用户管理角色下拉改角色列表。

## Technical Context

**Language/Version**: Java 17（后端）+ TypeScript/React 18（前端）

**Primary Dependencies**: Spring Boot 3.2、MyBatis-Plus、Spring AOP（权限切面）、antd 5（Tree/Checkbox）、JWT（fetchMe）

**Storage**: 3 张新表（role/role_menu/role_permission）+ Flyway V46 seed；User.role 关联 Role.code

**Testing**: JUnit 5 + Mockito（RoleService）、集成（RoleIT + 权限 403 校验）、前端（角色页渲染 + 菜单过滤/按钮显隐）

**Target Platform**: Web

**Project Type**: Web 应用（Spring Boot + React）

**Performance Goals**: fetchMe ≤ 50ms（菜单/权限查询一次）；角色配置保存 ≤ 100ms

**Constraints**: 内建角色（builtIn=1）不可删除、ADMIN 全量兜底；JWT 不放权限；权限码格式 `实体:动作`

**Scale/Scope**: 3 表 + 1 迁移 + 3 后端类 + auth/me 扩展 + 权限切面 + 前端角色页 + 菜单过滤 + 按钮显隐 + 用户管理联动

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则一：契约优先的 API 设计 | 端点契约先于实现 | ✅ 满足（contracts/role-permissions.md 定义角色 CRUD/配置/me 契约） |
| 原则二：分层架构与关注点分离 | Controller→Service→Repository | ✅ 满足（RoleService 统一事务，切面独立） |
| 原则三：数据完整性、安全与校验 | 服务端权限校验 | ✅ 满足（@RequirePermission 切面 + 内建角色保护） |
| 原则四：测试优先与质量门禁 | 测试先于实现 | ✅ 满足（RoleServiceTest/RoleIT + 前端测试） |
| 原则五：简洁、可维护与可观测 | 避免过度设计 | ✅ 满足（角色-菜单/权限两表简单关联，不引 RBAC 框架） |

**结论**: 无门禁违规。

## Project Structure

### Documentation (this feature)

```text
specs/028-role-permissions/
├── plan.md / spec.md / research.md / data-model.md / quickstart.md
├── contracts/（role-permissions 契约：角色端点 + 菜单/权限字典）
└── tasks.md
```

### Source Code (repository root)

```text
backend/src/main/java/com/crm/
├── entity/Role.java / RoleMenu.java / RolePermission.java      # 新增
├── repository/RoleMapper.java / RoleMenuMapper.java / RolePermissionMapper.java
├── dto/role/RoleRequest.java / RoleResponse.java / RoleOption.java
├── service/RoleService.java                                     # CRUD + 菜单/权限配置 + 字典
├── controller/RoleController.java                               # /api/v1/roles + /menu-tree + /permissions
├── security/RequirePermission.java                              # 注解
├── security/PermissionAspect.java                               # 切面校验（读 RolePermission + User.role）
├── service/AuthService.java                                     # 修改：me 返回 menus+permissions
└── dto/auth/UserInfo.java                                       # 修改：+ menus + permissions

backend/src/main/resources/db/migration/V46__role_permissions.sql  # 3 表 + seed（ADMIN/SALES/SUPPORT）
backend/src/test/java/com/crm/
├── service/RoleServiceTest.java                                 # 新增
└── integration/RoleIT.java                                      # 新增（CRUD + 403 校验 + me）

frontend/src/
├── types/role.ts                                                # 新增：Role/RoleOption/MenuKey/PermissionCode
├── services/roleService.ts                                      # 新增：fetchRoles/createRole/updateRole/deleteRole/configureRole
├── pages/roles/RoleListPage.tsx                                 # 新增：角色列表 + 编辑弹窗（基本信息+菜单树+权限勾选）
├── App.tsx                                                      # 修改：菜单按 user.menus 过滤 + 未授权路由守卫
├── store/authStore.ts                                           # 修改：UserInfo + menus/permissions
└── pages/users/UserManagementPage.tsx                           # 修改：角色下拉改角色列表
```

**Structure Decision**: 沿用既有分层。角色-菜单/权限用两张关联表（简单多对多）；权限校验用注解+切面（@RequirePermission("customer:delete")）读当前用户角色权限码，关键写操作标注；内建角色保护（builtIn 不可删、ADMIN 全量）。前端菜单过滤基于 fetchMe 返回的 menus（App.tsx 按分组过滤），按钮显隐用 `hasPerm` helper。

## Complexity Tracking

> 无违规，本表留空。
