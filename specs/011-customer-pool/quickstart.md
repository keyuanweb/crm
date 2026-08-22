# Quickstart: 客户公海与转移模块验证指南

**Branch**: `011-customer-pool`

## 前置条件

- MySQL（`crm_db`）、Redis 运行；后端服务运行于 `SERVER_PORT=8081`。
- Flyway 启动时自动应用 V23（customer 表 owner_id 列）。
- 前端开发服务运行（`VITE_PROXY_TARGET=http://localhost:8081`）。

## 验证场景

### 1. 后端验证（自动化）

```bash
cd backend
mvn verify                          # 全部单元/契约测试 + spotless + JaCoCo
mvn test "-Dtest=CustomerPoolIT"    # 公海集成测试
```

**预期**: BUILD SUCCESS；`*Test` 全绿；`*IT` 单独运行通过。

### 2. 线上端点验证（手动）

```bash
# 登录（admin）
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}'

# 建无归属客户 → 公海
curl -X POST http://localhost:8081/api/v1/customers \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"name":"公海客户","company":"公海公司"}'

# 公海列表
curl "http://localhost:8081/api/v1/customers/pool" -H "Authorization: Bearer <token>"

# 领取
curl -X POST http://localhost:8081/api/v1/customers/pool/<id>/claim \
  -H "Authorization: Bearer <token>"

# 我的客户
curl "http://localhost:8081/api/v1/customers/my" -H "Authorization: Bearer <token>"

# 公海扫描（超期退回）
curl -X POST http://localhost:8081/api/v1/customers/pool/scan \
  -H "Authorization: Bearer <token>"

# 批量转移
curl -X POST http://localhost:8081/api/v1/customers/batch-transfer \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"customerIds":[1,2],"targetOwnerId":5}'
```

**预期**: 无归属客户进公海；领取后归属本人；重复领取 409；扫描退回超期客户；批量转移归属更新；SALES 扫描 403。

### 3. 前端验证

- 客户列表页 `/customers`：新增"我的客户 / 公海客户"视图切换；公海视图可"领取"；管理员可"批量转移/分配"（弹窗选目标用户与客户）。
- 领取/转移后刷新归属正确；审计日志可查轨迹。

### 4. 契约核对

- 响应结构对照 `contracts/customer-pool.md`。
- Swagger: `http://localhost:8081/swagger-ui.html` → 客户分组可见新端点。
