# Quickstart: 多币种模块验证指南

**Branch**: `057-multi-currency`

## 前置条件

- MySQL（`crm_db`）、Redis 运行；后端服务运行于 `SERVER_PORT=8081`。
- Flyway 启动时自动应用 V65（currency_rate + product_price）。
- 前端开发服务运行（`VITE_PROXY_TARGET=http://localhost:8081`）。

## 验证场景

### 1. 后端验证（自动化）

```bash
cd backend
mvn verify                   # 全部单元/契约测试 + spotless + JaCoCo
mvn test "-Dtest=MultiCurrencyIT" # 多币种集成测试
```

**预期**: BUILD SUCCESS；全绿。

### 2. 线上端点验证（手动）

```bash
# 新增 USD 汇率 7.2
curl -X POST http://localhost:8081/api/v1/currencies \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"code":"USD","name":"美元","rate":7.2}'

# 折算 100000 分 CNY → USD
curl -X POST http://localhost:8081/api/v1/currencies/convert \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"amount":100000,"fromCurrency":"CNY","toCurrency":"USD"}'
# → convertedAmount ≈ 13889

# 产品设置 USD 价
curl -X POST http://localhost:8081/api/v1/products/1/prices \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"currencyCode":"USD","price":14000}'

# 产品价格视图
curl http://localhost:8081/api/v1/products/1/prices -H "Authorization: Bearer <token>"
```

**预期**: 汇率 CRUD/折算正确；产品多币种价保存与折算展示正确；基准 CNY 不可改。

### 3. 前端验证

- 流程与配置组"多币种"菜单 → 汇率管理页。
- 产品编辑多币种价；报价金额按币种折算展示。

### 4. 契约核对

- 响应结构对照 `contracts/multi-currency.md`。
- Swagger: `http://localhost:8081/swagger-ui.html` → 多币种分组。
