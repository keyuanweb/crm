# Quickstart: 任务与提醒模块验证指南

**Branch**: `010-task-reminder`

## 前置条件

- MySQL（`crm_db`）、Redis 运行；后端服务运行于 `SERVER_PORT=8081`。
- Flyway 启动时自动应用 V22（task_item）。
- 前端开发服务运行（`VITE_PROXY_TARGET=http://localhost:8081`）。

## 验证场景

### 1. 后端验证（自动化）

```bash
cd backend
mvn verify                      # 全部单元/契约测试 + spotless + JaCoCo
mvn test "-Dtest=TaskIT"        # 任务集成测试
```

**预期**: BUILD SUCCESS；`*Test` 全绿；`*IT` 单独运行通过。

### 2. 线上端点验证（手动）

```bash
# 登录（sales）
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" -d '{"username":"sales","password":"Passw0rd!"}'

# 创建逾期任务
curl -X POST http://localhost:8081/api/v1/tasks \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"title":"跟进旧客户","dueAt":"2026-08-10T10:00:00","priority":"HIGH"}'

# 创建今日到期任务
curl -X POST http://localhost:8081/api/v1/tasks \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"title":"今日跟进","dueAt":"<今天>T18:00:00"}'

# 列表（含提醒标识）+ 汇总 + 日历
curl "http://localhost:8081/api/v1/tasks?status=TODO" -H "Authorization: Bearer <token>"
curl http://localhost:8081/api/v1/tasks/reminder-summary -H "Authorization: Bearer <token>"
curl "http://localhost:8081/api/v1/tasks/calendar?month=2026-08" -H "Authorization: Bearer <token>"

# 完成/重开
curl -X POST http://localhost:8081/api/v1/tasks/<id>/toggle -H "Authorization: Bearer <token>"

# 跟进自动建任务
curl -X POST http://localhost:8081/api/v1/follow-ups \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"customerId":2,"method":"PHONE","content":"沟通","nextFollowUpAt":"2026-08-25T10:00:00","createTask":true}'
```

**预期**: 逾期任务提醒标识 OVERDUE+逾期天数；今日到期 TODAY；汇总计数正确；日历按月返回；toggle 切换状态；跟进自动建任务出现在任务列表。

### 3. 前端验证

- 任务列表页 `/tasks`：提醒汇总卡片（逾期/今日）、ProTable 列表（逾期/今日 Tag、优先级 Tag）、新增/编辑/完成/重开/删除。
- 日历视图页 `/tasks/calendar`：antd Calendar 月视图，有任务日期标记、逾期红色；点击日期展示当日任务。

### 4. 契约核对

- 响应结构对照 `contracts/tasks.md`。
- Swagger: `http://localhost:8081/swagger-ui.html` → 任务分组可见新端点。
