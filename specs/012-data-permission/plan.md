# Implementation Plan: 数据权限增强模块

**Branch**: `012-data-permission` | **Date**: 2026-08-22 | **Spec**: [spec.md](./spec.md)

## Summary

新增部门（Department）与客户共享（CustomerShare）实体，User 增加 department_id 与 data_scope 字段：部门树管理（仅 ADMIN）；行级数据权限（SELF/DEPT/DEPT_AND_CHILD/ALL）应用于客户列表/详情/编辑/删除（Service 显式过滤）；客户共享（归属者/管理员共享给指定用户，共享用户只读，含"共享给我"列表）。前端用户管理增加部门/数据权限设置，客户列表按权限过滤 + 共享管理。

## Technical Context

**Language/Version**: Java 17 / TypeScript 5（沿用既有技术栈）

**Primary Dependencies**: MyBatis-Plus、Spring Validation、AuditService

**Storage**: MySQL 新增 `department`、`customer_share` 表，`user` 表新增 department_id/data_scope 列（Flyway V24~V26）

**Testing**: JUnit 5 + Spring Boot Test（DepartmentServiceTest/DataPermissionServiceTest/CustomerShareServiceTest 单元、DataPermissionIT 集成）

**Target Platform**: Web（用户管理扩展 + 客户列表权限过滤 + 共享管理）

**Project Type**: 现有 Web 应用新增模块

**Performance Goals**: 部门树/过滤后客户列表 ≤1s（SC-DP01）

**Constraints**: 权限范围 SELF 默认/ADMIN 默认 ALL；DEPT_AND_CHILD=部门树子孙集合；共享只读；部门有成员/子部门不可删（409）；行级过滤在 CustomerService 显式应用

**Scale/Scope**: 部门/用户 ≤ 数百；客户 ≤ 数千

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | Controller→Service→持久层 | ✅ 满足（复用既有分层） |
| 原则三：数据完整性、安全与校验 | DTO 校验、服务端授权、行级数据隔离 | ✅ 满足（Bean Validation + @PreAuthorize + Service 层过滤） |
| 原则四：测试优先与质量门禁 | 测试先于实现、合并门禁 | ✅ 满足（单元/集成测试 + 既有门禁） |
| 原则五：简洁、可维护与可观测 | 无 N+1、审计 | ✅ 满足（批量装配 + AuditService） |

**结论**: 无门禁违规。

## Project Structure

```text
specs/012-data-permission/
├── spec.md / plan.md / tasks.md / research.md / data-model.md / quickstart.md
├── contracts/data-permission.md

backend/src/main/java/com/crm/
├── entity/Department.java + repository/DepartmentMapper.java
├── entity/CustomerShare.java + repository/CustomerShareMapper.java
├── entity/User.java（新增 departmentId/dataScope）+ dto/user/（扩展）
├── service/DepartmentService.java（部门树/CRUD/子孙集合）
├── service/DataPermissionService.java（scope→userId 集合解析）
├── service/CustomerShareService.java（共享 CRUD/共享给我）
├── controller/DepartmentController.java + CustomerShareController.java
├── common/ErrorCode.java（新增 DEPARTMENT_* / SHARE_* 错误码）
└── resources/db/migration/V24__department.sql + V25__user_data_scope.sql + V26__customer_share.sql

backend/src/test/java/com/crm/
├── service/DepartmentServiceTest.java + DataPermissionServiceTest.java + CustomerShareServiceTest.java
├── integration/DataPermissionIT.java

frontend/src/
├── types/department.ts + services/departmentService.ts
├── types/customerShare.ts + services/customerShareService.ts
├── pages/departments/DepartmentListPage.tsx（部门树管理，仅 ADMIN）
├── pages/users/UserManagementPage.tsx（部门/数据权限设置）
├── pages/customers/CustomerListPage.tsx（权限过滤 + 共享管理）
└── App.tsx（部门菜单 + 路由，仅 ADMIN）
```

**Structure Decision**: 沿用既有前后端目录结构（Option 2 Web 应用）。数据权限解析独立 `DataPermissionService`（scope→可见 owner 集合）；行级过滤在 `CustomerService` 列表/详情/写操作显式应用（spec 假设）。

## Complexity Tracking

无违规，本表留空。
