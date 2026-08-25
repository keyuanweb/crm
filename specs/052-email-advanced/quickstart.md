# Quickstart: 邮件高级能力模块验证指南

**Branch**: `052-email-advanced`

## 前置条件

- MySQL（`crm_db`）、Redis 运行；后端服务运行于 `SERVER_PORT=8081`。
- Flyway 启动时自动应用 V60（email_unsubscribe + 扩展列）。
- 前端开发服务运行（`VITE_PROXY_TARGET=http://localhost:8081`）。

## 验证场景

### 1. 后端验证（自动化）

```bash
cd backend
mvn verify                      # 全部单元/契约测试 + spotless + JaCoCo
mvn test "-Dtest=EmailAdvancedIT" # 邮件高级集成测试
```

**预期**: BUILD SUCCESS；全绿。

### 2. 线上端点验证（手动）

```bash
# 公开退订
curl -X POST http://localhost:8081/api/v1/public/email/unsubscribe \
  -H "Content-Type: application/json" -d '{"email":"cust@test.com"}'

# 退订名单（需 token）
curl http://localhost:8081/api/v1/email/unsubscribes -H "Authorization: Bearer <token>"

# 群发统计
curl http://localhost:8081/api/v1/email/campaigns/3/stats -H "Authorization: Bearer <token>"
```

**预期**: 退订成功；名单可见；统计数字正确；退订客户被后续群发排除。

### 3. 前端验证

- 退订名单页（列表/搜索/恢复）。
- 群发详情统计（发送/打开/点击率）；创建群发 A/B 配置。

### 4. 契约核对

- 响应结构对照 `contracts/email-advanced.md`。
- Swagger: `http://localhost:8081/swagger-ui.html` → 邮件分组。
