# Quickstart: 订单与回款模块验证指南

**Branch**: `009-order-payment`

## 前置条件

- MySQL（`crm_db`）、Redis 运行；后端服务运行于 `SERVER_PORT=8081`。
- Flyway 启动时自动应用 V19~V21（sales_order / payment_plan / payment_record）。
- 前端开发服务运行（`VITE_PROXY_TARGET=http://localhost:8081`）。

## 验证场景

### 1. 后端验证（自动化）

```bash
cd backend
mvn verify                          # 全部单元/契约测试 + spotless + JaCoCo
mvn test "-Dtest=OrderPaymentIT"    # 订单/回款集成测试
```

**预期**: BUILD SUCCESS；`*Test` 全绿；`*IT` 单独运行通过。

### 2. 线上端点验证（手动）

```bash
# 登录（admin）
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}'

# 创建订单（基于已生效合同 3，两期计划合计=订单金额）
curl -X POST http://localhost:8081/api/v1/orders \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"title":"CRM 采购订单","customerId":7,"contractId":3,"amount":200000,
       "plans":[{"amount":80000,"dueDate":"2026-09-01","description":"首付"},
                {"amount":120000,"dueDate":"2026-10-01","description":"尾款"}]}'

# 登记第 1 期回款
curl -X POST http://localhost:8081/api/v1/orders/<id>/payments \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"planId":<planId>,"amount":80000,"paidAt":"2026-08-22","method":"TRANSFER"}'

# 超额登记应 400
curl -X POST http://localhost:8081/api/v1/orders/<id>/payments \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"planId":<planId>,"amount":999999,"paidAt":"2026-08-22","method":"TRANSFER"}'

# 台账（详情含 plans/payments 与提醒标识）
curl http://localhost:8081/api/v1/orders/<id> -H "Authorization: Bearer <token>"
```

**预期**: 订单号 SO-YYYYMMDD-XXXX 自动生成；期次合计校验；回款后第 1 期 PAID、订单 PARTIAL；超额 400；台账提醒标识正确。

### 3. 前端验证

- 订单列表页 `/orders`：新建（可选合同）、列表/搜索/状态筛选。
- 订单详情页 `/orders/:id`：回款计划台账（每期应收/已收/未收/状态/逾期或临期标识）、回款记录表、登记回款弹窗（选择期次/金额/日期/方式）、删除按钮（仅管理员且无回款）。

### 4. 契约核对

- 响应结构对照 `contracts/orders.md`。
- Swagger: `http://localhost:8081/swagger-ui.html` → 订单分组可见新端点。
