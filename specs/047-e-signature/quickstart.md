# Quickstart: 电子签署模块验证指南

**Branch**: `047-e-signature`

## 前置条件

- MySQL（`crm_db`）、Redis 运行；后端服务运行于 `SERVER_PORT=8081`。
- Flyway 启动时自动应用 V58（signature_record）。
- 前端开发服务运行（`VITE_PROXY_TARGET=http://localhost:8081`）。

## 验证场景

### 1. 后端验证（自动化）

```bash
cd backend
mvn verify                          # 全部单元/契约测试 + spotless + JaCoCo
mvn test "-Dtest=ESignatureIT"      # 电子签署集成测试
```

**预期**: BUILD SUCCESS；全绿。

### 2. 线上端点验证（手动）

```bash
# 准备：报价单 APPROVED / 合同 APPROVED
# 签署报价单
curl -X POST http://localhost:8081/api/v1/quotes/1/sign \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"signatureImage":"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="}'
# → 201，报价单状态 SIGNED

# 重复签署 → 409
curl -X POST http://localhost:8081/api/v1/quotes/1/sign \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"signatureImage":"data:image/png;base64,AAAA"}'

# 合同签署 → 生效
curl -X POST http://localhost:8081/api/v1/contracts/1/sign \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"signatureImage":"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="}'
curl -X POST http://localhost:8081/api/v1/contracts/1/effective \
  -H "Authorization: Bearer <token>"   # → 200（已签署可生效）
```

**预期**: 报价/合同签署成功状态 SIGNED；重复签署 409；未签署合同生效 422；签署记录（人/时间/图）可查。

### 3. 前端验证

- 报价单/合同详情页"签署"区块：canvas 手绘签名 + 图片上传，提交后展示签署记录。
- 销售管理组"电子签署"菜单 → 签署记录页（可选）。

### 4. 契约核对

- 响应结构对照 `contracts/signature.md`。
- Swagger: `http://localhost:8081/swagger-ui.html` → 签署分组。
