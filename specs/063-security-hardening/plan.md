# Implementation Plan: 权限体系加固模块

**Branch**: `063-security-hardening` | **Date**: 2026-08-25 | **Spec**: [spec.md](./spec.md)

## Summary

复用 DataPermissionService（012）将行级数据权限扩展到 Lead/Contact/FollowUp/Comment 服务；导出（ExportExecutor）接入数据范围过滤 + MaskingUtil 脱敏；GlobalExceptionHandler 补 400/404/409 handler；5 组控制器补角色注解。

## Technical Context

**Language/Version**: Java 17 / TypeScript 5（沿用）

**Primary Dependencies**: DataPermissionService（012）、CustomerShare、MaskingUtil、GlobalExceptionHandler

**Storage**: 无迁移（复用现有表）

**Testing**: JUnit 5（各 ServiceTest 扩展越权场景 + ExceptionHandlerIT + ExportSecurityIT）

**Target Platform**: Web（无新页面；行为加固）

**Project Type**: 安全加固

**Performance Goals**: 过滤走 SQL IN 条件（不内存过滤）

**Constraints**: 非 ADMIN 行级过滤；ADMIN 豁免；共享继承

**Scale/Scope**: 核心实体（线索/联系人/跟进/评论）+ 导出 + 异常

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则三：数据完整性、安全与校验 | 行级权限全覆盖 | ✅ 满足 |
| 原则四：测试优先与质量门禁 | 越权测试先行 | ✅ 满足 |
| 原则五：简洁、可维护与可观测 | 复用现有组件 | ✅ 满足 |

**结论**: 无门禁违规。

## Project Structure

```text
specs/063-security-hardening/
├── spec.md / plan.md / tasks.md / research.md / quickstart.md
└── contracts/security-hardening.md

backend/src/main/java/com/crm/
├── service/LeadService.java（page 加 owner 过滤；detail/update/delete/assign/convert 加归属校验）
├── service/ContactService.java（按客户可见性过滤 + 写校验）
├── service/FollowUpService.java + CommentService.java（关联实体可见性过滤 + 写校验）
├── service/ExportExecutor.java（数据范围过滤 + MaskingUtil 脱敏）
├── controller/（Lead/Contact/FollowUp/Comment/CustomerShare 补 @PreAuthorize）
├── exception/GlobalExceptionHandler.java（补 400/404/409）
└── common/ErrorCode.java（如需新增）

backend/src/test/java/com/crm/
├── service/LeadServiceTest.java（+越权场景）
├── service/ContactServiceTest.java（+越权场景）
├── integration/SecurityHardeningIT.java（越权/导出/异常端到端）
└── integration/ExceptionHandlerIT.java（400/404/409）
```

**Structure Decision**: 服务层集中校验（复用 CustomerService.checkViewPermission/checkWritePermission 模式）；DataPermissionService.resolveVisibleOwnerIds 批量取可见 owner 集后 SQL IN 过滤。

## Complexity Tracking

无违规，本表留空。
