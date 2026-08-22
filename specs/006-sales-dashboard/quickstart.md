# Quickstart: 销售仪表盘模块验证指南

**Branch**: `006-sales-dashboard`

## 前置条件

- 后端依赖启动：MySQL（`crm_db`）、Redis；后端服务运行于 `SERVER_PORT=8081`。
- Flyway 已应用 V11（`sales_target` 表）——服务启动时自动迁移。
- 前端开发服务运行（`VITE_PROXY_TARGET=http://localhost:8081`）。

## 验证场景

### 1. 后端验证（自动化）

```bash
cd backend
mvn verify                      # 全部单元/契约测试 + spotless + JaCoCo
mvn test -Dtest=SalesTargetIT   # 目标设置/查询/权限集成测试
mvn test -Dtest=DashboardStatsIT # 仪表盘聚合集成测试
```

**预期**: BUILD SUCCESS；`*Test` 类全绿；`*IT` 类单独运行通过（与既有 `*IT` 约定一致）。

### 2. 线上端点验证（手动）

```bash
# 登录获取 token
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# 设置当月目标（ADMIN）
curl -X PUT http://localhost:8081/api/v1/stats/sales-targets \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"month":"2026-08","targetAmount":1000000}'

# 查询目标
curl http://localhost:8081/api/v1/stats/sales-targets?month=2026-08 \
  -H "Authorization: Bearer <token>"

# 仪表盘聚合
curl http://localhost:8081/api/v1/stats/dashboard -H "Authorization: Bearer <token>"
```

**预期**: 目标 upsert 幂等（重复 PUT 更新而非报错）；dashboard 返回 summary/funnel/forecast/performance/followUps/stalled 六段数据；非 ADMIN 角色 PUT 目标返回 403。

### 3. 前端验证

- 打开 `http://localhost:5173/stats`，应展示：4 张指标卡（商机总数/金额合计/赢单率/本月新增客户）、漏斗表格（阶段×数量×金额×转化率）、预测卡片（加权总额+分阶段明细）、业绩达成卡片（达成率+目标/已赢金额，未设目标时显示设置引导）、客户分析卡片、跟进报表（方式分布+最近记录）、停滞预警表格（空态正常）。
- 管理员设置目标后刷新，达成率卡片即时显示百分比。
- 无数据环境各卡片显示 0/空态，无报错。

### 4. 契约核对

- 响应结构对照 `contracts/stats.md`（dashboard/sales-targets 两段）。
- Swagger: `http://localhost:8081/swagger-ui.html` → 统计分组可见新端点。
