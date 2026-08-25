# Quickstart: 集成中心模块验证指南

**Branch**: `058-integration-hub`

## 前置条件

- MySQL（`crm_db`）、Redis 运行；后端服务运行于 `SERVER_PORT=8081`。
- Flyway 启动时自动应用 V66（integration_channel）。
- 前端开发服务运行（`VITE_PROXY_TARGET=http://localhost:8081`）。

## 验证场景

### 1. 后端验证（自动化）

```bash
cd backend
mvn verify                    # 全部单元/契约测试 + spotless + JaCoCo
mvn test "-Dtest=IntegrationHubIT" # 集成中心集成测试
```

**预期**: BUILD SUCCESS；全绿。

### 2. 线上端点验证（手动）

```bash
# 配置企业微信通道（URL 不可达 → 推送记录 FAILED）
curl -X POST http://localhost:8081/api/v1/integration-channels \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"channelType":"WECHAT_WORK","name":"销售群","webhookUrl":"http://localhost:9999/hook"}'

# 触发工单分配 → 推送记录
curl -X POST http://localhost:8081/api/v1/tickets/1/assign ... 

# 推送记录
curl http://localhost:8081/api/v1/integration-channels/1/deliveries -H "Authorization: Bearer <token>"
```

**预期**: 通道 CRUD 正常；事件触发推送记录（回调不可达 FAILED 重试）；停用通道不推送。

### 3. 前端验证

- 流程与配置组"集成中心"菜单 → 通道管理 + 推送记录。

### 4. 契约核对

- 响应结构对照 `contracts/integration-hub.md`。
- Swagger: `http://localhost:8081/swagger-ui.html` → 集成中心分组。
