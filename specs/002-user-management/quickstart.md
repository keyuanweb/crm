# Quickstart: 用户管理模块验证指南

**Branch**: `002-user-management`

## 前置条件

- MySQL（`crm_db`）、Redis 运行；后端服务运行于 `SERVER_PORT=8081`。
- Flyway 启动时自动应用 V4（user 表认证字段）。
- 前端开发服务运行（`VITE_PROXY_TARGET=http://localhost:8081`）。

## 验证场景

### 1. 后端验证（自动化）

```bash
cd backend
mvn verify                          # 全部单元/契约测试 + spotless + JaCoCo
mvn test "-Dtest=UserIT"            # 用户集成测试
```

**预期**: BUILD SUCCESS；`*Test` 全绿；`*IT` 单独运行通过。

### 2. 线上端点验证（手动）

```bash
# 登录 admin
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}'

# 创建用户（ADMIN 专属）
curl -X POST http://localhost:8081/api/v1/users \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"username":"sales01","password":"Passw0rd!","displayName":"销售一号","role":"SALES"}'

# 新用户登录（立即生效）
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" -d '{"username":"sales01","password":"Passw0rd!"}'

# 管理员重置密码 → 旧令牌立即失效
curl -X PUT http://localhost:8081/api/v1/users/2/password \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"newPassword":"NewPassw0rd!"}'

# 停用账号 → 无法再登录
curl -X PUT http://localhost:8081/api/v1/users/2 \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"displayName":"销售一号","role":"SALES","enabled":false,"version":0}'
```

**预期**: 新用户创建后可立即登录；重置/修改密码后旧令牌请求被 401 拒绝；停用账号登录被拒；无法停用最后一个启用 ADMIN。

### 3. 前端验证

- 用户管理页 `/users`（仅 ADMIN 菜单）：列表/搜索/筛选/创建/编辑/启停/重置密码。
- 个人中心修改密码（任意登录用户）。
- 用户详情含最后登录时间。

### 4. 契约核对

- 响应结构对照 `contracts/users.md`；通用约定见 001 契约 README。
- Swagger: `http://localhost:8081/swagger-ui.html` → 用户分组可见新端点。
