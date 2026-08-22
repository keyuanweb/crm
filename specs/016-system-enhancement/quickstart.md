# Quickstart: 系统增强模块验证指南

**Branch**: `016-system-enhancement`

## 前置条件

- MySQL（`crm_db`）、Redis 运行；后端服务运行于 `SERVER_PORT=8081`。
- Flyway 启动时自动应用 V38~V41（custom_field/custom_field_value/export_job/notification）。
- 前端开发服务运行（`VITE_PROXY_TARGET=http://localhost:8081`）。

## 验证场景

### 1. 后端验证（自动化）

```bash
cd backend
mvn verify                          # 全部单元/契约测试 + spotless + JaCoCo
mvn test "-Dtest=SystemEnhancementIT"  # 系统增强集成测试
```

**预期**: BUILD SUCCESS；`*Test` 全绿；`*IT` 单独运行通过。

### 2. 线上端点验证（手动）

```bash
# 登录（admin）
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}'

# 自定义字段：配置（仅 ADMIN）→ 实体携带值 → 筛选
curl -X POST http://localhost:8081/api/v1/custom-fields \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"entityType":"LEAD","name":"预算规模","fieldType":"NUMBER","required":false}'
curl -X POST http://localhost:8081/api/v1/leads \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"name":"线索A","company":"公司A","customFieldValues":[{"fieldId":1,"value":"500万"}]}'
curl "http://localhost:8081/api/v1/leads?cf_1=500万" -H "Authorization: Bearer <token>"

# 通知中心：分配工单 → 通知 → 已读 → 未读计数
curl -X POST http://localhost:8081/api/v1/tickets/1/assign -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" -d '{"assigneeId":5}'
curl "http://localhost:8081/api/v1/notifications" -H "Authorization: Bearer <token>"
curl "http://localhost:8081/api/v1/notifications/unread-count" -H "Authorization: Bearer <token>"
curl -X POST http://localhost:8081/api/v1/notifications/read-all -H "Authorization: Bearer <token>"

# 数据导出：创建任务 → 轮询状态 → 下载
curl -X POST http://localhost:8081/api/v1/exports \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"exportType":"LEAD","filter":{}}'
curl "http://localhost:8081/api/v1/exports" -H "Authorization: Bearer <token>"
curl -OJ "http://localhost:8081/api/v1/exports/1/download" -H "Authorization: Bearer <token>"
```

**预期**: 字段配置后创建实体表单出现对应字段且值可筛选；通知已读/未读计数正确；导出任务异步完成且下载文件行数与筛选一致。

### 3. 前端验证

- 设置-自定义字段页 `/settings/custom-fields`：按实体配置字段（仅 ADMIN）。
- 线索/客户/商机/工单详情页与创建弹窗：自定义字段展示与填写。
- 顶栏通知角标 + 通知抽屉（已读/全部已读）。
- 导出中心页 `/exports`：发起导出、历史与下载。
- 移动端窄屏（<768px）下表单/列表布局可用。

### 4. 契约核对

- 响应结构对照 `contracts/custom-fields.md`、`contracts/notifications.md`、`contracts/exports.md`。
- Swagger: `http://localhost:8081/swagger-ui.html` → 系统增强分组可见新端点。
