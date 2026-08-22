# Quickstart: 线索管理模块验证指南

**Branch**: `004-lead-management`

## 前置条件

- MySQL（`crm_db`）、Redis 运行；后端服务运行于 `SERVER_PORT=8081`。
- Flyway 启动时自动应用 V7~V9（lead 表、follow_up.lead_id、customer_id 可空）。
- 前端开发服务运行（`VITE_PROXY_TARGET=http://localhost:8081`）。

## 验证场景

### 1. 后端验证（自动化）

```bash
cd backend
mvn verify                              # 全部单元/契约测试 + spotless + JaCoCo
mvn test "-Dtest=LeadIT"                # 线索集成测试
mvn test "-Dtest=LeadContractTest"      # 线索契约测试
```

**预期**: BUILD SUCCESS；全绿。

### 2. 线上端点验证（手动）

```bash
# 登录 admin
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}'

# 建线索（进入线索池）
curl -X POST http://localhost:8081/api/v1/leads \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"name":"张三","company":"Acme 科技","source":"EXHIBITION"}'

# 线索池列表（仅未分配 NEW/WORKING）
curl "http://localhost:8081/api/v1/leads?poolOnly=true" -H "Authorization: Bearer <token>"

# 领取 → 分配 → 转化
curl -X POST http://localhost:8081/api/v1/leads/1/claim -H "Authorization: Bearer <token>"
curl -X POST http://localhost:8081/api/v1/leads/1/assign?ownerId=1 -H "Authorization: Bearer <token>"
curl -X POST http://localhost:8081/api/v1/leads/1/convert -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" -d '{"opportunityName":"Acme 采购","expectedAmount":1000000}'
# 预期：客户+联系人+商机创建，线索 QUALIFIED，重复转化 422
```

**预期**: 线索池/领取/分配/转化全链路正确；转化幂等（重复转化拒绝）。

### 3. 前端验证

- 线索列表页 `/leads`：全部/线索池 Tab、搜索筛选、新增/编辑、领取/分配/转化。
- 线索详情页：跟进时间线、转化信息与关联客户跳转。
- Excel 导入导出（模板下载、批量导入、导出）。

### 4. 契约核对

- 响应结构对照 `contracts/leads.md`。
- Swagger: `http://localhost:8081/swagger-ui.html` → 线索分组。
