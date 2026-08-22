# Quickstart: 产品与报价模块验证指南

**Branch**: `007-product-cpq`

## 前置条件

- MySQL（`crm_db`）、Redis 运行；后端服务运行于 `SERVER_PORT=8081`。
- Flyway 启动时自动应用 V12~V14（product / quote / quote_item）。
- 前端开发服务运行（`VITE_PROXY_TARGET=http://localhost:8081`）。

## 验证场景

### 1. 后端验证（自动化）

```bash
cd backend
mvn verify                        # 全部单元/契约测试 + spotless + JaCoCo
mvn test "-Dtest=ProductIT,QuoteIT"  # 产品/报价集成测试（与既有 *IT 约定一致）
```

**预期**: BUILD SUCCESS；`*Test` 全绿；`*IT` 单独运行通过。

### 2. 线上端点验证（手动）

```bash
# 登录（admin / sales）
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}'

# 创建产品（ADMIN）
curl -X POST http://localhost:8081/api/v1/products \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"code":"CRM-STD","name":"CRM 标准版","unit":"套","standardPrice":980000}'

# 创建报价单（草稿，关联客户 2）
curl -X POST http://localhost:8081/api/v1/quotes \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"customerId":2,"items":[{"productId":1,"quantity":2,"discount":0.75}]}'

# 提交 → 审批通过 → 导出 PDF
curl -X POST http://localhost:8081/api/v1/quotes/<id>/submit -H "Authorization: Bearer <token>"
curl -X POST http://localhost:8081/api/v1/quotes/<id>/approve -H "Authorization: Bearer <token>"
curl -o quote.pdf http://localhost:8081/api/v1/quotes/<id>/pdf -H "Authorization: Bearer <token>"
```

**预期**: 产品创建 201；报价总额 = 980000×2×0.75 = 1470000（分）；状态流转 DRAFT→PENDING_APPROVAL→APPROVED；PDF 可下载且中文正常。

### 3. 前端验证

- 产品管理页 `/products`：新增/编辑/停用产品，列表/搜索正常。
- 报价单页 `/quotes`：创建（行编辑：选产品/数量/折扣，动态合计）、提交；详情页审批按钮（仅 ADMIN 可见）；导出 PDF 下载。
- 非管理员访问产品写操作/审批按钮不可见且接口返回 403。

### 4. 契约核对

- 响应结构对照 `contracts/products-quotes.md`。
- Swagger: `http://localhost:8081/swagger-ui.html` → 产品/报价分组可见新端点。
