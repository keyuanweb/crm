# 快速开始：客户 360 与健康度评分

## 后端

1. Flyway `V42__health_score_config.sql`：建 `health_score_config` 表 + 5 条默认维度种子（跟进活跃度 30 / 回款及时性 25 / 工单 20 / 合作深度 15 / 近期互动 10）。
2. `HealthScoreConfig` 实体 + `HealthScoreConfigMapper`。
3. `HealthScoreService`：读配置 → 按维度计算 0-100 分 + 颜色 + 失分原因。
4. `Customer360Service`：聚合订单/回款/合同/工单/金额汇总（批量装配无 N+1）。
5. `CustomerService.detail()` 组合 360 聚合 + 健康度。
6. `CustomerController`：`GET /customers/health/at-risk` 预警列表。
7. 测试：`HealthScoreServiceTest`（单元）+ `Customer360IT`（集成）。

## 前端

1. `types/customer.ts`：新增 Customer360Response / HealthScoreDTO / CustomerHealthBrief 类型。
2. `services/customerService.ts`：`fetchCustomer360`（复用 fetchCustomer 但用新类型）/ `fetchAtRiskCustomers`。
3. `CustomerDetailPage.tsx`：新增"客户 360"Tabs（概览/订单回款/合同/工单），概览含健康度评分卡 + 金额汇总。
4. `AtRiskCustomersPage.tsx`：流失预警列表 + 一键跟进。
5. `App.tsx`：注册预警页路由（数据分析分组下）。

## 验证

- 后端：`mvn test`（HealthScoreServiceTest + Customer360IT 新增，不影响既有 203 测试）。
- 前端：`pnpm run typecheck` + `lint` + `test`（新增 CustomerDetailPage 渲染用例）。
- 手动：客户详情页可见 360 Tabs 与评分；预警页可见久未跟进客户并可跟进。
