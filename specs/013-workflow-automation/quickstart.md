# Quickstart: 工作流自动化模块验证指南

**Branch**: `013-workflow-automation`

## 前置条件

- MySQL（`crm_db`）、Redis 运行；后端服务运行于 `SERVER_PORT=8081`。
- Flyway 启动时自动应用 V27~V29（workflow_rule / workflow_execution_log / workflow_notification）。
- 前端开发服务运行（`VITE_PROXY_TARGET=http://localhost:8081`）。

## 验证场景

### 1. 后端验证（自动化）

```bash
cd backend
mvn verify                      # 全部单元/契约测试 + spotless + JaCoCo
mvn test "-Dtest=WorkflowIT"    # 工作流集成测试
```

**预期**: BUILD SUCCESS；`*Test` 全绿；`*IT` 单独运行通过。

### 2. 线上端点验证（手动）

```bash
# 登录（admin）
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}'

# 建规则：线索创建 → 自动分配（目标用户）
curl -X POST http://localhost:8081/api/v1/workflows/rules \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"name":"线索自动分配","eventType":"LEAD_CREATED",
       "actionType":"ASSIGN","action":{"targetUserId":5}}'

# 建规则：商机阶段变更→创建任务
curl -X POST http://localhost:8081/api/v1/workflows/rules \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"name":"谈判创建任务","eventType":"OPPORTUNITY_STAGE_CHANGED",
       "condition":{"field":"stage","value":"NEGOTIATING"},
       "actionType":"CREATE_TASK","action":{"titleTemplate":"跟进{name}","dueDays":3}}'

# 创建线索 → 自动分配生效
curl -X POST http://localhost:8081/api/v1/leads \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"name":"自动分配线索","company":"自动化公司"}'

# 执行日志
curl http://localhost:8081/api/v1/workflows/logs -H "Authorization: Bearer <token>"
```

**预期**: 线索创建后 ownerId 自动变为目标用户；商机阶段变更后自动创建任务；执行日志完整；停用规则后不再触发。

### 3. 前端验证

- 规则管理页 `/workflows`（仅管理员）：规则列表/新增/编辑/启停。
- 执行日志页：日志列表（事件/规则/结果/时间）筛选。

### 4. 契约核对

- 响应结构对照 `contracts/workflow.md`。
- Swagger: `http://localhost:8081/swagger-ui.html` → 工作流分组可见新端点。
