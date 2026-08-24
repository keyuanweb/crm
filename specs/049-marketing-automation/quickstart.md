# Quickstart: 营销自动化模块验证指南

**Branch**: `049-marketing-automation`

## 前置条件

- MySQL（`crm_db`）、Redis 运行；后端服务运行于 `SERVER_PORT=8081`。
- 前端开发服务运行（`VITE_PROXY_TARGET=http://localhost:8081`）。
- 本模块无迁移（复用 workflow_rule 表）。

## 验证场景

### 1. 后端验证（自动化）

```bash
cd backend
mvn verify                            # 全部单元/契约测试 + spotless + JaCoCo
mvn test "-Dtest=MarketingAutomationIT"  # 营销自动化集成测试
```

**预期**: BUILD SUCCESS；全绿。

### 2. 规则配置（前端）

1. 管理员进入"工作流"配置页。
2. 新建规则：
   - 事件 LEAD_SCORE_THRESHOLD，条件 `score=80`，动作 SEND_EMAIL（选模板）或 CREATE_TASK。
   - 事件 TAG_CHANGED，条件 `tag=高意向`，动作 ADD_TAG。
3. 启用规则。

### 3. 线上端点验证（手动）

```bash
# 线索评分重算（019）→ 触发 LEAD_SCORE_THRESHOLD
# 线索打标（031）→ 触发 TAG_CHANGED
# 查看工作流执行日志（013 页面）确认匹配/执行结果
```

**预期**: 评分达阈值线索触发邮件/任务；打标触发加标签；无匹配规则安静跳过；失败隔离。

### 4. 契约核对

- 无新接口（规则配置沿用 013 /workflows）；事件/动作类型为既有枚举扩展。
- Swagger: `http://localhost:8081/swagger-ui.html` → 工作流分组。
