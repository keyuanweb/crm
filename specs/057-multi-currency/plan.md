# Implementation Plan: 多币种模块

**Branch**: `057-multi-currency` | **Date**: 2026-08-25 | **Spec**: [spec.md](./spec.md)

## Summary

新增 `currency_rate`（币种+汇率）与 `product_price`（产品多币种价）两表。CurrencyRateService（CRUD/折算）+ ProductPriceService（多币种价 CRUD/折算展示）。金额折算：基准金额 × 汇率（分四舍五入）。

## Technical Context

**Language/Version**: Java 17 / TypeScript 5（沿用）

**Primary Dependencies**: ProductService（007）、MyBatis-Plus

**Storage**: MySQL 新增 `currency_rate` + `product_price` 表（V65）

**Testing**: JUnit 5（CurrencyRateServiceTest/ProductPriceServiceTest 单元、MultiCurrencyIT 集成）

**Target Platform**: Web（汇率管理页 + 产品编辑多币种价 + 报价币种折算展示）

**Project Type**: 平台能力（新增）+ 007 扩展

**Performance Goals**: 折算计算 ≤1ms（汇率内存缓存）

**Constraints**: 基准 CNY 恒 1；配置仅 ADMIN（汇率）/ADMIN+SALES（产品价）；金额存储不迁移

**Scale/Scope**: 币种 ≤ 数十；产品价 ≤ 数百

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | 独立汇率/价格服务 | ✅ 满足 |
| 原则三：数据完整性、安全与校验 | 汇率校验、唯一约束 | ✅ 满足 |
| 原则四：测试优先与质量门禁 | 测试先于实现 | ✅ 满足 |
| 原则五：简洁、可维护与可观测 | 内存缓存、纯函数折算 | ✅ 满足 |

**结论**: 无门禁违规。

## Project Structure

```text
specs/057-multi-currency/
├── spec.md / plan.md / tasks.md / research.md / data-model.md / quickstart.md
├── contracts/multi-currency.md

backend/src/main/java/com/crm/
├── entity/（CurrencyRate/ProductPrice）+ repository/（2 Mapper）
├── dto/currency/（CurrencyRateRequest/Response/ProductPriceRequest/Response/ConvertRequest）
├── service/CurrencyRateService.java（CRUD/汇率缓存/convert）
├── service/ProductPriceService.java（多币种价 CRUD/价格折算视图）
├── controller/CurrencyRateController.java + ProductPriceController.java
├── common/ErrorCode.java（新增 CURRENCY_* 错误码）
└── resources/db/migration/V65__multi_currency.sql

backend/src/test/java/com/crm/
├── service/CurrencyRateServiceTest.java + ProductPriceServiceTest.java
├── integration/MultiCurrencyIT.java

frontend/src/
├── services/currencyService.ts + types/currency.ts
├── pages/settings/CurrencyRatePage.tsx（汇率管理）
├── 产品编辑多币种价 + 报价金额折算展示
└── App.tsx（流程与配置组点亮"多币种"占位项 → 路由）
```

**Structure Decision**: CurrencyRateService 内存缓存汇率（启动加载）；convert 纯函数；产品价查询接口返回 基准价+折算价。

## Complexity Tracking

无违规，本表留空。
