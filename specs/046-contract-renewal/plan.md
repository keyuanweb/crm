# Implementation Plan: 合同续约管理模块

**Branch**: `046-contract-renewal` | **Date**: 2026-08-25 | **Spec**: [spec.md](./spec.md)

## Summary

扩展合同续约能力：`contract` 表新增 `renewed_from_id`（自引用续约来源）；提供续约视图接口（即将到期 90 天/已到期未续/已续约 分组 + 搜索分页）；合同创建可选续约来源；详情展示续约来源/去向。

## Technical Context

**Language/Version**: Java 17 / TypeScript 5（沿用）

**Primary Dependencies**: MyBatis-Plus、Spring Validation、AuditService

**Storage**: MySQL `contract` 表新增 `renewed_from_id` 列（Flyway V57）

**Testing**: JUnit 5（ContractRenewalServiceTest 单元、ContractRenewalIT 集成）

**Target Platform**: Web（续约视图页 + 合同创建/详情续约字段）

**Project Type**: 既有模块增强（008-contract-management）

**Performance Goals**: 续约视图查询 ≤1s

**Constraints**: 仅 EFFECTIVE 合同参与到期提醒；阈值 90 天；renewed_from_id 引用需存在

**Scale/Scope**: 合同量级数千

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | Controller→Service→持久层 | ✅ 满足 |
| 原则三：数据完整性、安全与校验 | DTO 校验、服务端授权 | ✅ 满足（Bean Validation + @PreAuthorize） |
| 原则四：测试优先与质量门禁 | 测试先于实现、合并门禁 | ✅ 满足 |
| 原则五：简洁、可维护与可观测 | 无 N+1、审计 | ✅ 满足（批量装配 + AuditService） |

**结论**: 无门禁违规。

## Project Structure

```text
specs/046-contract-renewal/
├── spec.md / plan.md / tasks.md / research.md / data-model.md / quickstart.md
├── contracts/renewal.md

backend/src/main/java/com/crm/
├── entity/Contract.java（+renewedFromId）
├── dto/contract/（ContractRequest/Response +renewedFromId/renewal 信息；ContractRenewalResponse）
├── service/ContractRenewalService.java（到期归类/续约漏斗/来源去向）
├── controller/ContractRenewalController.java（/contracts/renewal-overview）
├── common/ErrorCode.java（新增 CONTRACT_RENEWAL_* 错误码）
└── resources/db/migration/V57__contract_renewal.sql

backend/src/test/java/com/crm/
├── service/ContractRenewalServiceTest.java
├── integration/ContractRenewalIT.java

frontend/src/
├── services/contractService.ts（+renewal 接口）
├── pages/contracts/ContractRenewalPage.tsx（续约漏斗视图）
├── 合同创建/详情续约来源字段
└── App.tsx（交易管理组点亮"续约管理"占位项 → 路由）
```

**Structure Decision**: 续约视图独立 Service/Controller（避免污染既有 ContractService 生命周期逻辑）；合同创建在 ContractService 中校验 renewedFromId。

## Complexity Tracking

无违规，本表留空。
