# Implementation Plan: 客户公海与转移模块

**Branch**: `011-customer-pool` | **Date**: 2026-08-22 | **Spec**: [spec.md](./spec.md)

## Summary

在既有 Customer 上新增 `owner_id`（归属销售）字段：公海客户（owner 为空）列表与领取；"我的客户"（owner=本人）数据隔离；管理员手动执行公海扫描（最近跟进/创建超 N 天退回，默认 30 可配置 `crm.pool.stale-days`）；批量转移（归属 A→B）与公海批量分配（空→C，单次 ≤100）；领取/退回/转移记审计。前端客户列表增加"我的/公海"视图与领取/转移操作。

## Technical Context

**Language/Version**: Java 17 / TypeScript 5（沿用既有技术栈）

**Primary Dependencies**: MyBatis-Plus、Spring Validation、AuditService

**Storage**: MySQL `customer` 表新增 `owner_id` 列（Flyway V23）；复用 follow_up 与 audit_log

**Testing**: JUnit 5 + Spring Boot Test（CustomerPoolServiceTest 单元、CustomerPoolIT 集成）

**Target Platform**: Web（客户列表页扩展：我的/公海视图、领取、批量转移）

**Project Type**: 现有 Web 应用新增模块

**Performance Goals**: 公海/我的客户列表 ≤1s（SC-PL01）

**Constraints**: 已归属客户不可被他人领取（409）；目标用户须存在（404）；扫描仅 ADMIN；批量 ≤100；超期判定基于最近跟进时间（无跟进按创建时间）

**Scale/Scope**: 客户数量级 ≤ 数千；批量单次 ≤100

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | Controller→Service→持久层 | ✅ 满足（复用既有分层） |
| 原则三：数据完整性、安全与校验 | DTO 校验、服务端授权、数据隔离 | ✅ 满足（Bean Validation + @PreAuthorize + owner 校验） |
| 原则四：测试优先与质量门禁 | 测试先于实现、合并门禁 | ✅ 满足（单元/集成测试 + 既有门禁） |
| 原则五：简洁、可维护与可观测 | 无 N+1、审计 | ✅ 满足（批量装配 + AuditService） |

**结论**: 无门禁违规。

## Project Structure

```text
specs/011-customer-pool/
├── spec.md / plan.md / tasks.md / research.md / data-model.md / quickstart.md
├── contracts/customer-pool.md

backend/src/main/java/com/crm/
├── entity/Customer.java（新增 owner_id 字段）
├── dto/pool/（ClaimRequest/BatchTransferRequest/PoolScanResult）
├── service/CustomerPoolService.java（公海列表/领取/扫描退回/批量转移/审计）
├── controller/CustomerPoolController.java（GET /customers/pool、POST claim、POST scan、POST batch-transfer）
├── common/ErrorCode.java（新增 CUSTOMER_ALREADY_OWNED / USER_NOT_FOUND 复用 / POOL_SCAN...）
└── resources/db/migration/V23__customer_owner.sql（owner_id 列 + 索引）

backend/src/test/java/com/crm/
├── service/CustomerPoolServiceTest.java
├── integration/CustomerPoolIT.java

frontend/src/
├── types/customer.ts（扩展 ownerId/ownerName）
├── services/customerService.ts（pool 列表/领取/扫描/批量转移）
├── pages/customers/CustomerListPage.tsx（我的/公海视图切换 + 领取 + 批量转移）
└── （App.tsx 路由复用 /customers）
```

**Structure Decision**: 沿用既有前后端目录结构（Option 2 Web 应用）。公海逻辑独立 `CustomerPoolService` 保持与 CustomerService 关注点分离；客户列表页扩展视图切换而非新建页面。

## Complexity Tracking

无违规，本表留空。
