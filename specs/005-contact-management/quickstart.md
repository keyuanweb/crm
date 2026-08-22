# Quickstart: 联系人管理模块验证指南

**Branch**: `005-contact-management`

## 前置条件

- MySQL（`crm_db`）、Redis 运行；后端服务运行于 `SERVER_PORT=8081`。
- Flyway 启动时自动应用 V10（contact 表）。
- 前端开发服务运行（`VITE_PROXY_TARGET=http://localhost:8081`）。

## 验证场景

### 1. 后端验证（自动化）

```bash
cd backend
mvn verify                              # 全部单元/契约测试 + spotless + JaCoCo
mvn test "-Dtest=ContactIT"             # 联系人集成测试
```

**预期**: BUILD SUCCESS；全绿。

### 2. 线上端点验证（手动）

```bash
# 登录 admin
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}'

# 建客户
curl -X POST http://localhost:8081/api/v1/customers \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"name":"Acme 科技","company":"Acme Inc"}'

# 客户下建联系人（角色决策者）
curl -X POST http://localhost:8081/api/v1/customers/1/contacts \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"name":"张三","phone":"13800000000","role":"DECISION_MAKER"}'

# 重复联系人 → 409
curl -X POST http://localhost:8081/api/v1/customers/1/contacts \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"name":"张三","phone":"13800000000","role":"OTHER"}'

# 联系人列表（关键字/角色筛选）
curl "http://localhost:8081/api/v1/contacts?keyword=%E5%BC%A0&page=1&pageSize=20" \
  -H "Authorization: Bearer <token>"
```

**预期**: 联系人创建/编辑/逻辑删除正常；同客户重复（姓名+电话）409；关键字/角色筛选正确；客户详情聚合联系人列表。

### 3. 前端验证

- 客户详情页"联系人"区块：添加/编辑/删除/列表。
- 联系人列表页（若独立入口）：关键字/客户/角色筛选。

### 4. 契约核对

- 响应结构对照 `contracts/contacts.md`。
- Swagger: `http://localhost:8081/swagger-ui.html` → 联系人分组。
