# Quickstart: 权限体系加固模块验证指南

**Branch**: `063-security-hardening`

## 前置条件

- MySQL（`crm_db`）、Redis 运行；后端服务运行于 `SERVER_PORT=8081`。
- 无迁移（复用现有表）；前端无新页面。

## 验证场景

### 1. 后端验证（自动化）

```bash
cd backend
mvn verify                      # 全部测试 + spotless + JaCoCo（含既有 135+ 无回归）
mvn test "-Dtest=SecurityHardeningIT,ExceptionHandlerIT" # 越权/导出/异常端到端
```

**预期**: BUILD SUCCESS；全绿。

### 2. 线上端点验证（手动）

```bash
# 越权：SALES-A 查看/修改 owner=SALES-B 的线索 → 403
curl http://localhost:8081/api/v1/leads/<b-lead-id> -H "Authorization: Bearer <tokenA>"   # 403

# 导出：SALES 导出仅含本人数据 + 手机号脱敏
curl -X POST http://localhost:8081/api/v1/exports -H "Authorization: Bearer <tokenSALES>" \
  -d '{"type":"CUSTOMER"}' && curl http://localhost:8081/api/v1/exports/<id>/download ...

# 异常：坏 JSON → 400；重复用户名 → 409；不存在路径 → 404
curl -X POST http://localhost:8081/api/v1/customers -H "Content-Type: application/json" -d '{bad'  # 400
```

**预期**: 越权 403、导出过滤+脱敏、400/404/409 状态码正确。

### 3. 契约核对

- 响应语义对照 `contracts/security-hardening.md`（无新端点）。
