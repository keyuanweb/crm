# Implementation Plan: 产品与报价模块

**Branch**: `007-product-cpq` | **Date**: 2026-08-22 | **Spec**: [spec.md](./spec.md)

## Summary

新增产品目录（Product）与报价单（Quote + QuoteItem）两大实体：产品 CRUD（编码唯一、标准售价）；报价单支持行明细（产品快照、数量、折扣、自动算额）、状态机（DRAFT→PENDING_APPROVAL→APPROVED/REJECTED）、管理员审批与 OpenPDF 中文 PDF 导出。前端新增产品管理页与报价单列表/详情页。

## Technical Context

**Language/Version**: Java 17 / TypeScript 5（沿用既有技术栈）

**Primary Dependencies**: MyBatis-Plus、Spring Validation、OpenPDF 1.3.30（PDF 导出）、AuditService

**Storage**: MySQL 新增 `product`、`quote`、`quote_item` 三表（Flyway V12~V14）

**Testing**: JUnit 5 + Spring Boot Test（ProductServiceTest/QuoteServiceTest 单元、ProductIT/QuoteIT 集成）

**Target Platform**: Web（产品管理页 + 报价单列表/详情页）

**Project Type**: 现有 Web 应用新增模块

**Performance Goals**: 报价列表/创建 ≤1s（SC-P01）

**Constraints**: 产品编码唯一（409）；报价状态机合法流转（409）；金额分精度；行小计=单价×数量×(1-折扣)；PDF 中文无乱码

**Scale/Scope**: 产品/报价数量级 ≤ 数千；单报价行数 ≤ 数十

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | Controller→Service→持久层 | ✅ 满足（复用既有分层） |
| 原则三：数据完整性、安全与校验 | DTO 校验、服务端授权 | ✅ 满足（Bean Validation + @PreAuthorize 角色控制） |
| 原则四：测试优先与质量门禁 | 测试先于实现、合并门禁 | ✅ 满足（单元/集成测试 + 既有门禁） |
| 原则五：简洁、可维护与可观测 | 无 N+1、审计 | ✅ 满足（列表批量装配 + AuditService） |

**结论**: 无门禁违规。

## Project Structure

```text
specs/007-product-cpq/
├── spec.md / plan.md / tasks.md / research.md / data-model.md / quickstart.md
├── contracts/products-quotes.md

backend/src/main/java/com/crm/
├── entity/Product.java + repository/ProductMapper.java
├── entity/Quote.java + repository/QuoteMapper.java
├── entity/QuoteItem.java + repository/QuoteItemMapper.java
├── dto/product/ProductRequest.java + ProductResponse.java
├── dto/quote/QuoteRequest.java + QuoteResponse.java + QuoteItemRequest.java + QuoteItemResponse.java
├── service/ProductService.java + service/QuoteService.java
├── service/QuotePdfService.java（OpenPDF 生成，含中文字体）
├── controller/ProductController.java + controller/QuoteController.java
├── common/ErrorCode.java（新增 PRODUCT_* / QUOTE_* 错误码）
└── resources/db/migration/V12__product.sql + V13__quote.sql + V14__quote_item.sql
└── resources/fonts/wqy-microhei.ttc（开源中文字体）

backend/src/test/java/com/crm/
├── service/ProductServiceTest.java + service/QuoteServiceTest.java
├── integration/ProductIT.java + integration/QuoteIT.java（含 PDF 导出）

frontend/src/
├── types/product.ts + services/productService.ts
├── types/quote.ts + services/quoteService.ts
├── pages/products/ProductListPage.tsx
├── pages/quotes/QuoteListPage.tsx + pages/quotes/QuoteDetailPage.tsx
└── App.tsx（产品/报价菜单 + 路由）
```

**Structure Decision**: 沿用既有前后端目录结构（Option 2 Web 应用）。PDF 生成独立 `QuotePdfService` 保持关注点分离；字体打包进 resources 保证可移植。

## Complexity Tracking

无违规，本表留空。
