# Quickstart: SLA 工作时间与节假日日历模块验证指南

**Branch**: `054-sla-calendar`

## 前置条件

- MySQL（`crm_db`）、Redis 运行；后端服务运行于 `SERVER_PORT=8081`。
- Flyway 启动时自动应用 V61（sla_calendar_config）。
- 前端开发服务运行（`VITE_PROXY_TARGET=http://localhost:8081`）。

## 验证场景

### 1. 后端验证（自动化）

```bash
cd backend
mvn verify                    # 全部单元/契约测试 + spotless + JaCoCo
mvn test "-Dtest=SlaCalendarIT" # SLA 日历集成测试
```

**预期**: BUILD SUCCESS；全绿。

### 2. 配置与计算验证（手动）

```bash
# 配置工作时间 09:00-18:00，周一至周五，启用
curl -X PUT http://localhost:8081/api/v1/sla-calendar \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"workSlots":[{"start":"09:00","end":"18:00"}],"workDays":[1,2,3,4,5],"holidays":["2026-10-01"],"enabled":true}'

# 周五 17:00 创建工单（SLA 响应 2h）→ 到期应为周一 11:00
```

**预期**: 工单 SLA 到期跳过周末/非工作时间；无配置时回退 24h。

### 3. 前端验证

- 流程与配置组"SLA日历"菜单 → 工作时间/节假日配置页（多时间段/工作周/节假日列表）。

### 4. 契约核对

- 响应结构对照 `contracts/sla-calendar.md`。
- Swagger: `http://localhost:8081/swagger-ui.html` → SLA 分组。
