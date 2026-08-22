# Quickstart: 数据权限增强模块验证指南

**Branch**: `012-data-permission`

## 前置条件

- MySQL（`crm_db`）、Redis 运行；后端服务运行于 `SERVER_PORT=8081`。
- Flyway 启动时自动应用 V24~V26（department / user 数据权限列 / customer_share）。
- 前端开发服务运行（`VITE_PROXY_TARGET=http://localhost:8081`）。

## 验证场景

### 1. 后端验证（自动化）

```bash
cd backend
mvn verify                        # 全部单元/契约测试 + spotless + JaCoCo
mvn test "-Dtest=DataPermissionIT" # 数据权限集成测试
```

**预期**: BUILD SUCCESS；`*Test` 全绿；`*IT` 单独运行通过。

### 2. 线上端点验证（手动）

```bash
# 登录（admin）
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}'

# 建部门
curl -X POST http://localhost:8081/api/v1/departments \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"name":"销售一部"}'
curl -X POST http://localhost:8081/api/v1/departments \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"name":"华东组","parentId":1}'

# 部门树
curl http://localhost:8081/api/v1/departments/tree -H "Authorization: Bearer <token>"

# 设置用户部门与数据权限
curl -X PUT http://localhost:8081/api/v1/users/<salesId>/data-permission \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"departmentId":1,"dataScope":"DEPT_AND_CHILD"}'

# 共享客户
curl -X POST http://localhost:8081/api/v1/customer-shares \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"customerId":1,"sharedToUserId":<salesB>}'

# 共享给我
curl http://localhost:8081/api/v1/customer-shares/shared-to-me \
  -H "Authorization: Bearer <token>"
```

**预期**: 部门树层级正确；用户权限配置生效；不同 scope 用户看到的客户列表不同；共享后对方可见且只读。

### 3. 前端验证

- 部门管理页 `/departments`（仅管理员）：部门树展示/新增/编辑/删除。
- 用户管理页：用户行设置部门与数据权限。
- 客户列表页：按权限过滤；详情可"共享"给用户（归属者/管理员）；"共享给我的客户"入口。

### 4. 契约核对

- 响应结构对照 `contracts/data-permission.md`。
- Swagger: `http://localhost:8081/swagger-ui.html` → 部门/客户共享分组可见新端点。
