# Quickstart: 工单满意度调查（CSAT/NPS）模块验证指南

**Branch**: `051-csat-nps`

## 前置条件

- MySQL（`crm_db`）、Redis 运行；后端服务运行于 `SERVER_PORT=8081`。
- Flyway 启动时自动应用 V59（ticket_survey）。
- 前端开发服务运行（`VITE_PROXY_TARGET=http://localhost:8081`）。

## 验证场景

### 1. 后端验证（自动化）

```bash
cd backend
mvn verify                          # 全部单元/契约测试 + spotless + JaCoCo
mvn test "-Dtest=TicketSurveyIT"    # 满意度集成测试
```

**预期**: BUILD SUCCESS；全绿。

### 2. 线上端点验证（手动）

```bash
# 准备：工单流转到 CLOSED
# 提交评分
curl -X POST http://localhost:8081/api/v1/tickets/1/survey \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"rating":5,"comment":"很满意"}'
# → 201

# 重复评分 → 409
curl -X POST http://localhost:8081/api/v1/tickets/1/survey \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"rating":3}'

# 满意度统计
curl "http://localhost:8081/api/v1/surveys/stats" \
  -H "Authorization: Bearer <token>"
```

**预期**: 评分保存；重复 409；统计返回 CSAT 均值/NPS 分布。

### 3. 前端验证

- 工单详情"满意度"区块：CLOSED 工单可评分（1-5 + 评语），已评分展示记录。
- 服务协作组"满意度调查"菜单 → CSAT/NPS 统计页（时间筛选/分布展示）。

### 4. 契约核对

- 响应结构对照 `contracts/survey.md`。
- Swagger: `http://localhost:8081/swagger-ui.html` → 满意度分组。
