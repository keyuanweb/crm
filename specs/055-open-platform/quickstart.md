# Quickstart: 开放平台模块验证指南

**Branch**: `055-open-platform`

## 前置条件

- MySQL（`crm_db`）、Redis 运行；后端服务运行于 `SERVER_PORT=8081`。
- Flyway 启动时自动应用 V63（api_key/webhook_subscription/webhook_delivery）。
- 前端开发服务运行（`VITE_PROXY_TARGET=http://localhost:8081`）。

## 验证场景

### 1. 后端验证（自动化）

```bash
cd backend
mvn verify                  # 全部单元/契约测试 + spotless + JaCoCo
mvn test "-Dtest=OpenPlatformIT" # 开放平台集成测试
```

**预期**: BUILD SUCCESS；全绿。

### 2. 线上端点验证（手动）

```bash
# 创建 API Key
curl -X POST http://localhost:8081/api/v1/platform/api-keys \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"name":"数据同步","scopes":["customer:read"]}'
# → 返回完整 key（ck_...）

# 用 API Key 调开放端点（无 JWT）
curl http://localhost:8081/api/v1/open/customers \
  -H "X-API-Key: ck_..."

# 配置 Webhook（事件 LEAD_CREATED）
curl -X POST http://localhost:8081/api/v1/platform/webhooks \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"eventType":"LEAD_CREATED","callbackUrl":"http://localhost:9999/hook"}'

# 创建线索触发推送 → 查看推送记录
curl -X POST http://localhost:8081/api/v1/leads ... 
curl http://localhost:8081/api/v1/platform/webhooks/1/deliveries -H "Authorization: Bearer <token>"
```

**预期**: Key 鉴权通过；无 Key 401；吊销后 401；Webhook 触发推送记录（失败则重试）。

### 3. 前端验证

- 系统管理组"开放平台"菜单 → API Key 页 + Webhook 页（订阅/推送记录）。

### 4. 契约核对

- 响应结构对照 `contracts/open-platform.md`。
- Swagger: `http://localhost:8081/swagger-ui.html` → 开放平台分组。
