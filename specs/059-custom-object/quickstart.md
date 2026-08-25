# Quickstart: 自定义对象模块验证指南

**Branch**: `059-custom-object`

## 前置条件

- MySQL（`crm_db`）、Redis 运行；后端服务运行于 `SERVER_PORT=8081`。
- Flyway 启动时自动应用 V67（custom_object + custom_object_record）。
- 前端开发服务运行（`VITE_PROXY_TARGET=http://localhost:8081`）。

## 验证场景

### 1. 后端验证（自动化）

```bash
cd backend
mvn verify                  # 全部单元/契约测试 + spotless + JaCoCo
mvn test "-Dtest=CustomObjectIT" # 自定义对象集成测试
```

**预期**: BUILD SUCCESS；全绿。

### 2. 线上端点验证（手动）

```bash
# 定义对象
curl -X POST http://localhost:8081/api/v1/custom-objects \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"name":"项目","code":"PROJECT","fields":[{"field":"name","label":"项目名称","type":"TEXT","required":true}],"enabled":true}'

# 创建记录
curl -X POST http://localhost:8081/api/v1/custom-objects/1/records \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"values":{"name":"CRM 重构"}}'

# 记录列表
curl http://localhost:8081/api/v1/custom-objects/1/records -H "Authorization: Bearer <token>"
```

**预期**: 对象定义/记录 CRUD/搜索正常；必填校验/停用拦截正确。

### 3. 前端验证

- 流程与配置组"自定义对象"菜单 → 对象定义页 + 记录管理页（动态表单）。

### 4. 契约核对

- 响应结构对照 `contracts/custom-object.md`。
- Swagger: `http://localhost:8081/swagger-ui.html` → 自定义对象分组。
