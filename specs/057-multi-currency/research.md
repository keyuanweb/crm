# Research: 多币种模块

**Branch**: `057-multi-currency` | **Date**: 2026-08-25

## 1. 汇率模型

**Decision**: `currency_rate` 表（code、name、rate 对基准、is_base、enabled）。基准 CNY 恒 rate=1 且不可改。折算：目标金额 = 基准金额 × rate（分，四舍五入）。CurrencyRateService 启动加载汇率到内存 Map（更新时失效重载），convert 纯函数。

**Rationale**: 单一基准简化折算；内存缓存避免每笔查询 DB。

## 2. 产品多币种价

**Decision**: `product_price` 表（product_id + currency_code + price 分，唯一）。产品基准价（standardPrice，CNY）不变；查询产品价格时返回基准价 + 各币种价（配置价优先，未配置按汇率折算）。

**Rationale**: 配置价覆盖汇率折算（特殊定价）；唯一约束防重复。

## 3. 存储与展示

**Decision**: v1 金额存储不迁移（报价/合同/订单仍存 CNY 分）；折算仅展示层。前端报价/产品页按选中币种调用折算接口展示。

**Rationale**: 金额迁移影响面大（报价/合同/订单/统计多处）；v1 折算展示满足国际化展示需求，迁移留待专项。
