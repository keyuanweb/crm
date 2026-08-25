# Implementation Plan: 字段级读写权限模块

**Branch**: `056-field-permission` | **Date**: 2026-08-25 | **Spec**: [spec.md](./spec.md)

## Summary

新增 `field_permission` 表（role_id + entity_type + field_id + permission 三态）。字段列表接口返回当前角色权限标记；CustomFieldService 保存自定义字段值时校验权限（HIDDEN 拒绝写入、READ_ONLY 拒绝修改、ADMIN 豁免）。

## Technical Context

**Language/Version**: Java 17 / TypeScript 5（沿用）

**Primary Dependencies**: CustomFieldService（016）、RoleService（028）、SecurityUtil

**Storage**: MySQL 新增 `field_permission` 表（V64）

**Testing**: JUnit 5（FieldPermissionServiceTest 单元、FieldPermissionIT 集成）

**Target Platform**: Web（字段权限配置页 + 字段列表权限标记 + 保存拦截）

**Project Type**: 既有模块增强（016/028）

**Performance Goals**: 权限查询 ≤10ms（角色级缓存）

**Constraints**: 配置仅 ADMIN；v1 仅自定义字段；服务端强制

**Scale/Scope**: 配置 ≤ 数百

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | 独立 FieldPermissionService | ✅ 满足 |
| 原则三：数据完整性、安全与校验 | 服务端强制、仅 ADMIN 配置 | ✅ 满足 |
| 原则四：测试优先与质量门禁 | 测试先于实现 | ✅ 满足 |
| 原则五：简洁、可维护与可观测 | 三态枚举、角色级缓存 | ✅ 满足 |

**结论**: 无门禁违规。

## Project Structure

```text
specs/056-field-permission/
├── spec.md / plan.md / tasks.md / research.md / data-model.md / quickstart.md
├── contracts/field-permission.md

backend/src/main/java/com/crm/
├── entity/FieldPermission.java + repository/FieldPermissionMapper.java
├── dto/field/（FieldPermissionRequest/Response/FieldPermissionView）
├── service/FieldPermissionService.java（CRUD + permissionOf(role, entity, field) + 校验）
├── service/CustomFieldService.java（字段列表加权限标记 + saveValues 校验）
├── controller/FieldPermissionController.java（/field-permissions 配置 + 字段列表应用）
├── common/ErrorCode.java（新增 FIELD_* 错误码）
└── resources/db/migration/V64__field_permission.sql

backend/src/test/java/com/crm/
├── service/FieldPermissionServiceTest.java
├── integration/FieldPermissionIT.java

frontend/src/
├── services/fieldPermissionService.ts + types/fieldPermission.ts
├── pages/settings/FieldPermissionPage.tsx（配置：角色×实体×字段 权限矩阵）
├── 自定义字段列表/表单接入权限标记（隐藏/只读）
└── App.tsx（流程与配置组点亮"字段权限"占位项 → 路由）
```

**Structure Decision**: FieldPermissionService 独立；CustomFieldService 注入其校验；字段列表 DTO 加 permission 字段。

## Complexity Tracking

无违规，本表留空。
