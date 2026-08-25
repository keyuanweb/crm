# Quickstart: 客户自助门户模块验证指南

**Branch**: `050-customer-portal`

## 前置条件

- MySQL（`crm_db`）、Redis 运行；后端服务运行于 `SERVER_PORT=8081`。
- 前端开发服务运行（`VITE_PROXY_TARGET=http://localhost:8081`）。
- 本模块无迁移（复用知识库/工单/联系人表）。

## 验证场景

### 1. 后端验证（自动化）

```bash
cd backend
mvn verify                        # 全部单元/契约测试 + spotless + JaCoCo
mvn test "-Dtest=CustomerPortalIT" # 门户集成测试
```

**预期**: BUILD SUCCESS；全绿。

### 2. 公开端点验证（手动，无需 token）

```bash
# 知识库浏览（需先有 PUBLISHED 文章）
curl "http://localhost:8081/api/v1/portal/articles"

# 在线提单（手机/邮箱需匹配既有联系人）
curl -X POST http://localhost:8081/api/v1/portal/tickets \
  -H "Content-Type: application/json" \
  -d '{"phone":"13800000000","email":"c@t.com","title":"使用问题","description":"无法导出","priority":"MEDIUM"}'

# 进度查询（双验证）
curl -X POST http://localhost:8081/api/v1/portal/tickets/status \
  -H "Content-Type: application/json" \
  -d '{"ticketNo":"TK-...","phone":"13800000000"}'
```

**预期**: 无需登录可访问；提单返回工单号；查进度返回状态与回复；不匹配 404/422。

### 3. 前端验证

- 访问 /portal（公开路由，不跳登录）：知识库浏览/搜索、提单表单、进度查询。
- 主系统登录不受影响。

### 4. 契约核对

- 响应结构对照 `contracts/portal.md`。
- Swagger: `http://localhost:8081/swagger-ui.html` → 客户门户分组。
