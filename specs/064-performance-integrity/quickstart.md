# Quickstart: 性能与数据完整性模块验证指南

**Branch**: `064-performance-integrity`

## 前置条件

- MySQL（`crm_db`）、Redis 运行；后端服务运行于 `SERVER_PORT=8081`。
- 无迁移；前端无改动。

## 验证场景

### 1. 后端验证（自动化）

```bash
cd backend
mvn verify                          # 全部测试 + spotless + JaCoCo
mvn test "-Dtest=CustomerAtRiskIT"  # 预警分页/owner 默认端到端
```

**预期**: BUILD SUCCESS；全绿。

### 2. 线上端点验证（手动）

```bash
# SALES 创建客户 → owner=当前用户
curl -X POST http://localhost:8081/api/v1/customers -H "Authorization: Bearer <tokenSALES>" \
  -H "Content-Type: application/json" -d '{"name":"新客户","company":"新公司"}'
# 响应 data.ownerId == 当前用户 id

# 流失预警（数据量小时行为不变）
curl http://localhost:8081/api/v1/customers/at-risk -H "Authorization: Bearer <token>"
```

**预期**: owner 默认正确；预警分页与判定不变。

### 3. 契约核对

- 响应语义对照 `contracts/performance-integrity.md`（无新端点）。
