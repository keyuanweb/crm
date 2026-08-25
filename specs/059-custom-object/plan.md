# Implementation Plan: 自定义对象模块

**Branch**: `059-custom-object` | **Date**: 2026-08-25 | **Spec**: [spec.md](./spec.md)

## Summary

新增 `custom_object`（名称/编码唯一/字段集 JSON/启用/逻辑删除）与 `custom_object_record`（对象 id/记录值 JSON）两表。CustomObjectService（对象 CRUD + 字段校验）+ CustomObjectRecordService（记录 CRUD + 搜索）。动态表单由前端按对象字段集渲染。

## Technical Context

**Language/Version**: Java 17 / TypeScript 5（沿用）

**Primary Dependencies**: MyBatis-Plus、Jackson（字段集/记录值 JSON）

**Storage**: MySQL 新增 `custom_object` + `custom_object_record` 表（V67）

**Testing**: JUnit 5（CustomObjectServiceTest/CustomObjectRecordServiceTest 单元、CustomObjectIT 集成）

**Target Platform**: Web（对象定义页 + 对象记录管理页）

**Project Type**: 平台能力（低代码）

**Performance Goals**: 记录 CRUD ≤50ms

**Constraints**: 对象定义仅 ADMIN；编码唯一；字段集 JSON 内嵌

**Scale/Scope**: 对象 ≤ 数十；记录 ≤ 数千

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | 独立对象/记录服务 | ✅ 满足 |
| 原则三：数据完整性、安全与校验 | 字段校验、编码唯一 | ✅ 满足 |
| 原则四：测试优先与质量门禁 | 测试先于实现 | ✅ 满足 |
| 原则五：简洁、可维护与可观测 | JSON 存储、审计 | ✅ 满足 |

**结论**: 无门禁违规。

## Project Structure

```text
specs/059-custom-object/
├── spec.md / plan.md / tasks.md / research.md / data-model.md / quickstart.md
├── contracts/custom-object.md

backend/src/main/java/com/crm/
├── entity/（CustomObject/CustomObjectRecord）+ repository/（2 Mapper）
├── dto/customobject/（ObjectRequest/ObjectResponse/RecordRequest/RecordResponse）
├── service/CustomObjectService.java（对象 CRUD/字段校验）
├── service/CustomObjectRecordService.java（记录 CRUD/搜索）
├── controller/CustomObjectController.java（/custom-objects + /custom-objects/{id}/records）
├── common/ErrorCode.java（新增 OBJECT_* 错误码）
└── resources/db/migration/V67__custom_object.sql

backend/src/test/java/com/crm/
├── service/CustomObjectServiceTest.java + CustomObjectRecordServiceTest.java
├── integration/CustomObjectIT.java

frontend/src/
├── services/customObjectService.ts + types/customObject.ts
├── pages/custom-object/CustomObjectListPage.tsx（对象定义）
├── pages/custom-object/CustomObjectRecordPage.tsx（记录管理：动态表单）
└── App.tsx（流程与配置组新增"自定义对象"菜单 → 路由）
```

**Structure Decision**: 双服务（对象定义与记录分离）；记录值 JSON 键值对；动态表单前端渲染。

## Complexity Tracking

无违规，本表留空。
