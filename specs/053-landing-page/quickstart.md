# Quickstart: 托管落地页 + UTM 跟踪模块验证指南

**Branch**: `053-landing-page`

## 前置条件

- MySQL（`crm_db`）、Redis 运行；后端服务运行于 `SERVER_PORT=8081`。
- Flyway 启动时自动应用 V62（landing_page + form_submission UTM 列）。
- 前端开发服务运行（`VITE_PROXY_TARGET=http://localhost:8081`）。

## 验证场景

### 1. 后端验证（自动化）

```bash
cd backend
mvn verify                    # 全部单元/契约测试 + spotless + JaCoCo
mvn test "-Dtest=LandingPageIT" # 落地页集成测试
```

**预期**: BUILD SUCCESS；全绿。

### 2. 公开端点验证（手动）

```bash
# 配置落地页（关联启用表单）
curl -X POST http://localhost:8081/api/v1/landing-pages \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"title":"夏季促销","formId":3,"enabled":true}'

# 公开渲染
curl http://localhost:8081/api/v1/public/lp/1

# 带 UTM 提交表单
curl -X POST "http://localhost:8081/api/v1/public/forms/3/submit?utm_source=facebook&utm_campaign=summer" \
  -H "Content-Type: application/json" -d '{"phone":"13800000000"}'

# UTM 统计
curl "http://localhost:8081/api/v1/landing-pages/1/stats" -H "Authorization: Bearer <token>"
```

**预期**: 公开渲染返回落地页+表单；带 UTM 提交快照记录 UTM；统计按来源聚合。

### 3. 前端验证

- 营销中心组"落地页"菜单 → 配置页（CRUD）。
- 公开 /lp/:id 页面渲染（标题/描述/表单）。
- 落地页详情统计（UTM 归因）。

### 4. 契约核对

- 响应结构对照 `contracts/landing-page.md`。
- Swagger: `http://localhost:8081/swagger-ui.html` → 落地页分组。
