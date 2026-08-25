# Quickstart: 通话记录管理模块验证指南

**Branch**: `061-call-center`

## 前置条件

- MySQL（`crm_db`）、Redis 运行；后端服务运行于 `SERVER_PORT=8081`。
- Flyway 启动时自动应用 V68（call_record）。
- 前端开发服务运行（`VITE_PROXY_TARGET=http://localhost:8081`）。

## 验证场景

### 1. 后端验证（自动化）

```bash
cd backend
mvn verify                # 全部单元/契约测试 + spotless + JaCoCo
mvn test "-Dtest=CallCenterIT" # 通话记录集成测试
```

**预期**: BUILD SUCCESS；全绿。

### 2. 线上端点验证（手动）

```bash
# 录入呼出记录
curl -X POST http://localhost:8081/api/v1/call-records \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"customerId":3,"contactId":5,"direction":"OUTBOUND","durationSeconds":300,"result":"CONNECTED","remark":"确认续约"}'

# 列表
curl http://localhost:8081/api/v1/call-records -H "Authorization: Bearer <token>"

# 统计
curl http://localhost:8081/api/v1/call-records/stats -H "Authorization: Bearer <token>"
```

**预期**: 记录 CRUD/统计正常；枚举/归属校验正确。

### 3. 前端验证

- 工作台组"通话记录"菜单 → 列表/录入/统计。

### 4. 契约核对

- 响应结构对照 `contracts/call-center.md`。
- Swagger: `http://localhost:8081/swagger-ui.html` → 通话记录分组。
