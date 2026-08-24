# Quickstart: 合同续约管理模块验证指南

**Branch**: `046-contract-renewal`

## 前置条件

- MySQL（`crm_db`）、Redis 运行；后端服务运行于 `SERVER_PORT=8081`。
- Flyway 启动时自动应用 V57（contract.renewed_from_id）。
- 前端开发服务运行（`VITE_PROXY_TARGET=http://localhost:8081`）。

## 验证场景

### 1. 后端验证（自动化）

```bash
cd backend
mvn verify                          # 全部单元/契约测试 + spotless + JaCoCo
mvn test "-Dtest=ContractRenewalIT" # 续约集成测试
```

**预期**: BUILD SUCCESS；全绿。

### 2. 线上端点验证（手动）

```bash
# 登录 admin（含验证码）
# 准备：客户 + 生效合同 A（endDate 设为临期）
# 创建续约合同 B（renewedFromId=合同A）
curl -X POST http://localhost:8081/api/v1/contracts \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"contractNo":"HT2026B","title":"续约合同","customerId":3,"amount":1200000,
       "startDate":"2026-08-01","endDate":"2027-08-01","renewedFromId":1}'

# 续约漏斗视图
curl "http://localhost:8081/api/v1/contracts/renewal-overview?group=EXPIRING_SOON" \
  -H "Authorization: Bearer <token>"
curl "http://localhost:8081/api/v1/contracts/renewal-overview?group=RENEWED" \
  -H "Authorization: Bearer <token>"
```

**预期**: 合同 A 归入"即将到期"；建立续约后 A 归入"已续约"且显示去向，B 显示续约来源。

### 3. 前端验证

- 交易管理组"续约管理"菜单 → 续约漏斗视图（分组切换/搜索/跳转详情）。
- 合同创建弹窗可选"续约自"合同；合同详情展示来源/去向。

### 4. 契约核对

- 响应结构对照 `contracts/renewal.md`。
- Swagger: `http://localhost:8081/swagger-ui.html` → 合同分组。
