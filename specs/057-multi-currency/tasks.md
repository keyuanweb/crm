# Tasks: 多币种模块

**Input**: Design documents from `/specs/057-multi-currency/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2

---

## Phase 1: 数据模型与基础

- [ ] T001 [P] 创建 Flyway 迁移 V65 in `backend/src/main/resources/db/migration/V65__multi_currency.sql`（currency_rate + product_price 表 + 唯一约束）
- [ ] T002 [P] H2 测试 schema 同步 in `backend/src/test/resources/schema-h2.sql`
- [ ] T003 [P] 创建 CurrencyRate/ProductPrice 实体 + 2 Mapper in `backend/src/main/java/com/crm/entity/` + `backend/src/main/java/com/crm/repository/`
- [ ] T004 [P] ErrorCode 新增 CURRENCY_* 错误码 in `backend/src/main/java/com/crm/common/ErrorCode.java`
- [ ] T005 [P] 创建 DTO in `backend/src/main/java/com/crm/dto/currency/`

**Checkpoint**: 数据模型就绪

---

## Phase 2: 用户故事 1 - 汇率管理 (P0)

**Goal**: 币种 CRUD + 折算。

**Independent Test**: 新增 USD → 折算正确 → 基准不可改。

### 实现

- [ ] T006 [P] [US1] 创建 CurrencyRateService（CRUD/基准保护/汇率缓存/convert）in `backend/src/main/java/com/crm/service/CurrencyRateService.java`
- [ ] T007 [US1] 创建 CurrencyRateController（/currencies + /currencies/convert，仅 ADMIN 管理）in `backend/src/main/java/com/crm/controller/CurrencyRateController.java`
- [ ] T008 [US1] 创建 CurrencyRateServiceTest 单元测试 in `backend/src/test/java/com/crm/service/CurrencyRateServiceTest.java`

**Checkpoint**: US1 可用——汇率管理

---

## Phase 3: 用户故事 2 - 产品多币种价格 (P0)

**Goal**: 产品多币种价 CRUD + 折算视图。

**Independent Test**: 设置 USD 价 → 视图含配置价/折算价。

### 实现

- [ ] T009 [P] [US2] 创建 ProductPriceService（CRUD/价格视图：配置价优先/未配置折算）in `backend/src/main/java/com/crm/service/ProductPriceService.java`
- [ ] T010 [US2] 创建 ProductPriceController（/products/{id}/prices）in `backend/src/main/java/com/crm/controller/ProductPriceController.java`
- [ ] T011 [US2] 创建 ProductPriceServiceTest 单元测试 in `backend/src/test/java/com/crm/service/ProductPriceServiceTest.java`
- [ ] T012 [US2] 创建 MultiCurrencyIT 集成测试 in `backend/src/test/java/com/crm/integration/MultiCurrencyIT.java`
- [ ] T013 [US2] 前端类型/服务 in `frontend/src/types/currency.ts` + `frontend/src/services/currencyService.ts`
- [ ] T014 [US2] 汇率管理页 in `frontend/src/pages/settings/CurrencyRatePage.tsx`
- [ ] T015 [US2] 产品编辑多币种价 + 报价金额折算展示 in `frontend/src/pages/products/` + `frontend/src/pages/quotes/`

**Checkpoint**: US2 可用——产品多币种价

---

## Phase 4: 收尾与验证

- [ ] T016 App.tsx 点亮"多币种"占位项为路由 in `frontend/src/App.tsx`（/currencies）
- [ ] T017 Backend `mvn verify` 通过（含新增测试、spotless、JaCoCo）
- [ ] T018 单独运行 `mvn test "-Dtest=MultiCurrencyIT"` 通过
- [ ] T019 Frontend typecheck / lint / test / build 通过
- [ ] T020 线上端点验证（汇率 CRUD→折算→产品价→基准保护）
- [ ] T021 更新契约文档（按实现校正）与 roadmap 057 标记 `[x]`

**Checkpoint**: 模块完整可用

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1**: 无依赖
- **Phase 2 (US1)**: 依赖 Phase 1
- **Phase 3 (US2)**: 依赖 Phase 1/2（折算依赖汇率）
- **Phase 4**: 依赖全部完成

## Notes

- 基准 CNY 恒 1 不可改
- v1 金额存储不迁移（折算仅展示）
- 提交规范：Conventional Commits
