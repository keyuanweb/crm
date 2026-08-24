# Quickstart: 销售 Playbook 模块验证指南

**Branch**: `045-sales-playbook`

## 前置条件

- MySQL（`crm_db`）、Redis 运行；后端服务运行于 `SERVER_PORT=8081`。
- Flyway 启动时自动应用 V55/V56（stage_action_template / sales_opportunity_action）。
- 前端开发服务运行（`VITE_PROXY_TARGET=http://localhost:8081`）。

## 验证场景

### 1. 后端验证（自动化）

```bash
cd backend
mvn verify                            # 全部单元/契约测试 + spotless + JaCoCo
mvn test "-Dtest=SalesPlaybookIT"     # Playbook 集成测试
```

**预期**: BUILD SUCCESS；全绿。

### 2. 线上端点验证（手动）

```bash
# 登录 admin（含验证码）
curl -X POST http://localhost:8081/api/v1/auth/captcha

# 配置动作模板
curl -X POST http://localhost:8081/api/v1/stage-actions \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"stage":"INITIAL_CONTACT","actionName":"发送产品资料","required":true}'

# 销售机会动作清单（需先有销售机会 id）
curl http://localhost:8081/api/v1/sales-opportunities/1/actions \
  -H "Authorization: Bearer <token>"

# 勾选完成
curl -X POST http://localhost:8081/api/v1/sales-opportunities/1/actions \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"templateId":1}'

# 再次勾选 → 409
curl -X POST http://localhost:8081/api/v1/sales-opportunities/1/actions \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"templateId":1}'
```

**预期**: 模板配置成功；动作清单含完成状态；勾选完成持久化；重复勾选 409；流转时未完成必做项有提示。

### 3. 前端验证

- 销售管理组"销售Playbook"菜单（原占位点亮）→ 动作模板配置页：按阶段 CRUD/启停。
- 销售机会详情页：动作清单展示 + 勾选完成 + 必做项未完成流转提示。

### 4. 契约核对

- 响应结构对照 `contracts/playbook.md`。
- Swagger: `http://localhost:8081/swagger-ui.html` → 销售 Playbook 分组。
