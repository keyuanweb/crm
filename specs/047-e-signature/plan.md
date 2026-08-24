# Implementation Plan: 电子签署模块

**Branch**: `047-e-signature` | **Date**: 2026-08-25 | **Spec**: [spec.md](./spec.md)

## Summary

报价单/合同电子签署：新增 SignatureRecord（单据类型/单据 id/签署人/时间/签名图 base64）；报价与合同扩展 SIGNED 状态；签署服务校验 APPROVED 前置、一次性、签名合法性；合同签署后才可生效。

## Technical Context

**Language/Version**: Java 17 / TypeScript 5（沿用）

**Primary Dependencies**: MyBatis-Plus、Spring Validation、AuditService

**Storage**: MySQL 新增 `signature_record` 表（Flyway V58）；quote/contract 状态枚举扩展 SIGNED

**Testing**: JUnit 5（SignatureServiceTest 单元、ESignatureIT 集成）

**Target Platform**: Web（报价/合同详情签署区块 + 签名绘制/上传）

**Project Type**: 既有模块增强（007/008）

**Performance Goals**: 签署操作 ≤1s

**Constraints**: 仅 APPROVED 可发起；一次签署；签名 ≤500KB base64；合同未签署不可生效

**Scale/Scope**: 签署记录 ≤ 数千

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | Controller→Service→持久层 | ✅ 满足 |
| 原则三：数据完整性、安全与校验 | DTO 校验、服务端授权 | ✅ 满足 |
| 原则四：测试优先与质量门禁 | 测试先于实现、合并门禁 | ✅ 满足 |
| 原则五：简洁、可维护与可观测 | 无 N+1、审计 | ✅ 满足（签署记录审计） |

**结论**: 无门禁违规。

## Project Structure

```text
specs/047-e-signature/
├── spec.md / plan.md / tasks.md / research.md / data-model.md / quickstart.md
├── contracts/signature.md

backend/src/main/java/com/crm/
├── entity/SignatureRecord.java + repository/SignatureRecordMapper.java
├── dto/signature/（SignRequest/SignatureRecordResponse）
├── service/SignatureService.java（发起校验/签署/记录查询）
├── controller/SignatureController.java（/quotes/{id}/sign + /contracts/{id}/sign + 记录查询）
├── service/QuoteService.java（+SIGNED 状态/签署后不可编辑）
├── service/ContractService.java（+SIGNED 状态/生效前置校验）
├── common/ErrorCode.java（新增 SIGNATURE_* 错误码）
└── resources/db/migration/V58__signature_record.sql

backend/src/test/java/com/crm/
├── service/SignatureServiceTest.java
├── integration/ESignatureIT.java

frontend/src/
├── types/signature.ts + services/signatureService.ts
├── 报价/合同详情签署区块（签名绘制 canvas + 上传 + 记录展示）
└── App.tsx（销售管理组点亮"电子签署"占位项 → 签署记录页）
```

**Structure Decision**: 签署独立 SignatureService（统一报价/合同）；状态扩展在既有 Service 最小改动；签名记录存 base64 文本列。

## Complexity Tracking

无违规，本表留空。
