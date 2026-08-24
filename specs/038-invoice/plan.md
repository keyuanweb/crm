# Implementation Plan: 发票管理

**Branch**: `038-invoice` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

## Summary

新增发票管理：`invoice` 表（订单开票：抬头/税号/金额/类型/编号/状态）；`InvoiceService`（开票（编号规则 + 可开额校验）+ 状态流转（作废需原因 + 审计）+ 列表筛选 + 开票率统计）；`InvoiceController`（/api/v1/invoices）；前端发票列表页（交易分组）+ 订单详情开票入口。

## Technical Context

**Language/Version**: Java 17（后端）+ TypeScript/React 18（前端）

**Primary Dependencies**: MyBatis-Plus、SalesOrderMapper（金额/可开额）、013 回款

**Storage**: 1 表 + Flyway V53

**Testing**: JUnit 5 + Mockito（开票/金额校验/作废/统计）、集成（InvoiceIT）、前端（发票列表渲染）

**Target Platform**: Web

**Project Type**: Web 应用

**Performance Goals**: 开票 ≤ 100ms；统计聚合 ≤ 200ms

**Constraints**: 累计开票 ≤ 订单金额；作废需原因 + 审计；编号 INV-{yyyyMM}-{seq}

**Scale/Scope**: 1 表 + 1 迁移 + 1 Service + 1 Controller + 前端发票列表页

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则一：契约优先的 API 设计 | 端点契约先于实现 | ✅ 满足（contracts/invoice.md） |
| 原则二：分层架构与关注点分离 | Controller→Service→Repository | ✅ 满足（InvoiceService 独立） |
| 原则三：数据完整性、安全与校验 | 金额校验/审计 | ✅ 满足（累计开票校验 + 作废原因 + 审计） |
| 原则四：测试优先与质量门禁 | 测试先于实现 | ✅ 满足（InvoiceServiceTest/InvoiceIT + 前端测试） |
| 原则五：简洁、可维护与可观测 | 避免过度设计 | ✅ 满足（预留编号/抬头，不接税控平台） |

**结论**: 无门禁违规。

## Project Structure

### Source Code

```text
backend/src/main/java/com/crm/
├── entity/Invoice.java
├── repository/InvoiceMapper.java
├── dto/invoice/InvoiceRequest.java / InvoiceResponse.java / InvoiceStatsResponse.java
├── service/InvoiceService.java        # 开票（编号 + 可开额校验）+ 作废（原因 + 审计）+ 列表 + 统计
├── controller/InvoiceController.java  # /api/v1/invoices + /stats
└── security/RequirePermission         # invoice:manage

backend/src/main/resources/db/migration/V53__invoice.sql
backend/src/test/java/com/crm/
├── service/InvoiceServiceTest.java
└── integration/InvoiceIT.java

frontend/src/
├── types/invoice.ts / services/invoiceService.ts
├── pages/invoices/InvoiceListPage.tsx   # 发票列表（交易分组：筛选 + 开票 + 作废 + 统计卡）
└── App.tsx                              # 路由（交易分组）
```

**Structure Decision**: 开票：校验订单存在 + 金额 ≤ 订单剩余可开额（累计开票 - 作废）→ 编号 INV-{yyyyMM}-{seq}（当日序号，并发用数据库 max+1 + 乐观）→ 状态 DRAFT(待开)/ISSUED(已开)/VOID(已作废)；作废需原因 + 审计 + 释放可开额。统计：按订单/客户开票率（已开金额/订单金额）。前端交易分组加"发票"页（统计卡 + 列表 + 开票/作废弹窗）+ 订单详情开票入口。

## Complexity Tracking

> 无违规，本表留空。
