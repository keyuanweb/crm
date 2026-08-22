# Quickstart: 市场营销模块验证指南

**Branch**: `014-marketing`

## 前置条件

- MySQL（`crm_db`）、Redis 运行；后端服务运行于 `SERVER_PORT=8081`。
- Flyway 启动时自动应用 V32~V33（marketing_campaign + lead/customer campaign_id）。
- 前端开发服务运行（`VITE_PROXY_TARGET=http://localhost:8081`）。

## 验证场景

### 1. 后端验证（自动化）

```bash
cd backend
mvn verify                      # 全部单元/契约测试 + spotless + JaCoCo
mvn test "-Dtest=MarketingIT"   # 营销集成测试
```

**预期**: BUILD SUCCESS；`*Test` 全绿；`*IT` 单独运行通过。

### 2. 线上端点验证（手动）

```bash
# 登录（admin）
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}'

# 建活动
curl -X POST http://localhost:8081/api/v1/campaigns \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"name":"广告活动","channel":"AD","budget":100000,"cost":50000}'

# 创建线索并归因
curl -X POST http://localhost:8081/api/v1/leads \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"name":"归因线索","company":"营销公司","campaignId":1}'

# 活动列表（含归因计数）+ 渠道 ROI
curl "http://localhost:8081/api/v1/campaigns" -H "Authorization: Bearer <token>"
curl http://localhost:8081/api/v1/campaigns/channel-roi -H "Authorization: Bearer <token>"

# 状态流转 + 删除防护
curl -X POST http://localhost:8081/api/v1/campaigns/1/start -H "Authorization: Bearer <token>"
curl -X POST http://localhost:8081/api/v1/campaigns/1/end -H "Authorization: Bearer <token>"
curl -X DELETE http://localhost:8081/api/v1/campaigns/1 -H "Authorization: Bearer <token>" # → 409
```

**预期**: 活动创建；线索归因后活动 leadCount 正确；渠道 ROI 聚合正确；ENDED 不可回退；有归因删除 409。

### 3. 前端验证

- 营销活动页 `/marketing`：列表/新增/编辑/开始/结束/删除。
- 渠道 ROI 页 `/marketing/roi`：各渠道活动数/成本/线索/客户/转化率/ROI 表格。
- 线索/客户创建弹窗增加"营销活动"选择。

### 4. 契约核对

- 响应结构对照 `contracts/marketing.md`。
- Swagger: `http://localhost:8081/swagger-ui.html` → 营销分组可见新端点。
