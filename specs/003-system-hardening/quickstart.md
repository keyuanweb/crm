# Quickstart: 系统加固与优化模块验证指南

**Branch**: `003-system-hardening`

## 前置条件

- MySQL（`crm_db`）、Redis 运行；后端服务运行于 `SERVER_PORT=8081`。
- Flyway 启动时自动应用 V6（customer active_key 生成列 + 唯一索引）。
- 前端开发服务运行（`VITE_PROXY_TARGET=http://localhost:8081`）。

## 验证场景

### 1. 后端验证（自动化）

```bash
cd backend
mvn verify                          # 全部单元/契约测试 + spotless + JaCoCo
```

**预期**: BUILD SUCCESS；含唯一约束、缓存降级、CORS 相关测试全绿。

### 2. 线上端点验证（手动）

```bash
# 登录 admin
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}'

# 并发唯一性：同名客户第二次创建 → 409 CUSTOMER_DUPLICATE
curl -X POST http://localhost:8081/api/v1/customers \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"name":"并发客户","company":"并发公司"}'
curl -X POST http://localhost:8081/api/v1/customers \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"name":"并发客户","company":"并发公司"}'
# 预期第二次 409（DB 唯一索引兜底）

# 停用用户后认证立即失效（UserStateCache evict）
curl -X PUT http://localhost:8081/api/v1/users/2 \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"displayName":"x","role":"SALES","enabled":false,"version":0}'
# 该用户旧令牌请求 → 401
```

**预期**: 重复客户 409；停用用户旧令牌即时 401；停 Redis 后认证降级正常（后端日志无崩溃）。

### 3. 前端验证

- 客户列表创建重复名称客户时提示唯一冲突。
- 用户停用后对应账号立即无法操作（会话失效）。

### 4. 契约核对

- 本模块无新增端点；错误码/状态码沿用 001 契约 README。
- Swagger: `http://localhost:8081/swagger-ui.html` 确认现有分组不受影响。
