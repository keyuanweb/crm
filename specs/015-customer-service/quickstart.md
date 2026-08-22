# Quickstart: 客户服务模块验证指南

**Branch**: `015-customer-service`

## 前置条件

- MySQL（`crm_db`）、Redis 运行；后端服务运行于 `SERVER_PORT=8081`。
- Flyway 启动时自动应用 V34~V37（ticket/ticket_reply/knowledge_article/sla_policy）。
- 前端开发服务运行（`VITE_PROXY_TARGET=http://localhost:8081`）。

## 验证场景

### 1. 后端验证（自动化）

```bash
cd backend
mvn verify                      # 全部单元/契约测试 + spotless + JaCoCo
mvn test "-Dtest=CustomerServiceIT"  # 客户服务集成测试
```

**预期**: BUILD SUCCESS；`*Test` 全绿；`*IT` 单独运行通过。

### 2. 线上端点验证（手动）

```bash
# 登录（admin）
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}'

# 配置 SLA 策略
curl -X POST http://localhost:8081/api/v1/sla-policies \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"priority":"HIGH","respondHours":4,"resolveHours":24,"enabled":true}'

# 创建工单（自动计算 SLA 到期时间）
curl -X POST http://localhost:8081/api/v1/tickets \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"customerId":3,"title":"登录失败","priority":"HIGH"}'

# 分配处理人 + 回复 + 流转 + 列表（含 SLA 状态）
curl -X POST http://localhost:8081/api/v1/tickets/1/assign -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" -d '{"assigneeId":5}'
curl -X POST http://localhost:8081/api/v1/tickets/1/reply -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" -d '{"content":"已联系客户"}'
curl -X POST http://localhost:8081/api/v1/tickets/1/transition -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" -d '{"targetStatus":"IN_PROGRESS"}'
curl "http://localhost:8081/api/v1/tickets" -H "Authorization: Bearer <token>"

# 知识库：创建→发布→搜索
curl -X POST http://localhost:8081/api/v1/knowledge -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" -d '{"category":"FAULT_TROUBLESHOOTING","title":"如何重置密码","content":"步骤"}'
curl -X POST http://localhost:8081/api/v1/knowledge/1/publish -H "Authorization: Bearer <token>"
curl "http://localhost:8081/api/v1/knowledge?keyword=密码" -H "Authorization: Bearer <token>"

# SLA 超时统计
curl "http://localhost:8081/api/v1/tickets/sla-overview" -H "Authorization: Bearer <token>"
```

**预期**: 工单创建后 SLA 到期时间按策略计算；状态单向流转（CLOSED 后再流转 409）；知识库搜索不返回草稿；超时统计正确。

### 3. 前端验证

- 工单列表页 `/tickets`：列表/筛选/创建/分配/回复/流转。
- 工单详情页 `/tickets/:id`：信息 + 回复时间线 + SLA 状态。
- 知识库页 `/knowledge`：文章列表/创建/发布/搜索。
- SLA 策略配置页 `/sla-policies`（仅 ADMIN 菜单可见）：策略 CRUD。

### 4. 契约核对

- 响应结构对照 `contracts/tickets.md`、`contracts/knowledge.md`、`contracts/sla.md`。
- Swagger: `http://localhost:8081/swagger-ui.html` → 客户服务分组可见新端点。
