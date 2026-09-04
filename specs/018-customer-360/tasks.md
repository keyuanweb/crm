# Tasks: 客户 360 画像与健康度评分



**Input**: Design documents from `/specs/018-customer-360/`



**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/customer-360.md



**Tests**: 后端单元（HealthScoreServiceTest）+ 集成（Customer360IT）+ 前端渲染测试（CustomerDetailPage.render.test.tsx）

## Phase 1: 基础设施搭建



- [x] T001 [P] 编写 Flyway `backend/src/main/resources/db/migration/V42__health_score_config.sql` 创建 `health_score_config` 表的 dimension_key/label/weight/params_json/enabled/sort_order 字段，插入 5 个评分维度的默认权重（跟进活跃度 30 / 回款及时性 25 / 工单/投诉 20 / 合作深度 15 / 近期互动 10）
- [x] T002 [P] 编写实体 `entity/HealthScoreConfig.java`（使用 Lombok @TableName）和 `repository/HealthScoreConfigMapper.java`
- [x] T003 [P] 编写 DTO `dto/customer/HealthScoreDTO.java`（包含 core/level/deductions 字段）、`dto/customer/CustomerHealthBrief.java`（预警列表项 DTO）和 `dto/customer/Customer360Response.java`（聚合 DTO）

## Phase 2: 后端测试

- [x] T004 [P] [US2] 编写 `backend/src/test/java/com/crm/service/HealthScoreServiceTest.java` 单元测试，覆盖评分维度/阈值/边界场景：跟进活跃、回款逾期、工单投诉、无数据等。测试 HealthScoreService 计算 0-100 分 + 红/黄/绿标识 + 失分原因。
- [x] T005 [P] [US1] 编写 `backend/src/test/java/com/crm/integration/Customer360IT.java` 集成测试，验证 GET /customers/{id} 返回完整的 orders/paymentSummaries/contracts/tickets/amountSummary/health，以及 GET /customers/health/at-risk 返回预警列表。

## Phase 3: 后端聚合服务实现

- [x] T006 [US1] 编写 `service/Customer360Service.java`，根据 customerId 聚合订单（SalesOrderMapper）、回款（PaymentPlanMapper/PaymentRecordMapper）、合同（ContractMapper）、工单（TicketMapper），计算金额汇总（totalOrder/paid/dueOverdue），复用现有批量装配模式避免 N+1
- [x] T007 [US1] 修改 `service/CustomerService.java` 的 `detail()` 方法，组合 Customer360Service 的结果，扩展 CustomerDetailResponse 返回 Customer360Response

## Phase 4: 后端评分服务实现

- [x] T008 [P] [US2] 编写 `service/HealthScoreService.java`，从 HealthScoreConfig 表读取可配置权重，基于 research.md R2 的评分规则计算 0-100 分 + 红/黄/绿标识（<60/60-79/≥80）+ 失分原因，支持 DEBUG 日志记录计算过程（T001/T002）
- [x] T009 [US2] 修改 `CustomerController`，在 `GET /customers/{id}` 中调用 HealthScoreService 返回 health 字段，新增 `GET /customers/health/at-risk` 接口，按健康度升序返回 N 个濒临流失客户（N 可配置）

## Phase 5: 前端测试

- [x] T010 [P] [US1] 编写 `frontend/src/pages/customers/CustomerDetailPage.render.test.tsx` 渲染测试，mock customerService 返回模拟数据，验证 360 Tabs（客户 360、健康度评分、预警建议）正确渲染，包括评分色标 + 失分原因 + 跟进按钮

## Phase 6: 前端实现

- [x] T011 [P] [US1] 修改 `types/customer.ts` 添加 Customer360Response/HealthScoreDTO/CustomerHealthBrief 类型定义，修改 `services/customerService.ts` 添加 `fetchCustomer360`/`fetchAtRiskCustomers` 方法
- [x] T012 [US1] 修改 `pages/customers/CustomerDetailPage.tsx`，添加"客户 360"Tabs 区块，展示订单/合同/工单汇总、健康度评分（Statistic + Tag + 红黄绿三色标识）、失分原因
- [x] T013 [US2] 编写 `pages/customers/AtRiskCustomersPage.tsx` 流失预警列表页，展示低分客户列表 + 一键跟进按钮，修改 `App.tsx` 注册预警页路由

## Phase 7: 质量检查

- [x] T014 运行 `mvn test` 确保 HealthScoreServiceTest + Customer360IT 全部通过（目标 203 个测试用例）
- [x] T015 运行 `npm run typecheck` + `lint` + `test` 确保前端类型检查通过，CustomerDetailPage 渲染测试全部通过
- [x] T016 [P] 人工审查 360 Tabs 展示的客户 360 聚合数据是否正确，评分与预警逻辑是否符合 spec.md 需求

## Dependencies & Execution Order



- T001/T002/T003 依赖基础设施搭建，可并行执行
- T004 依赖 T001/T002，T005 依赖 T003
- T006 依赖 T001/T002/T003，T007 依赖 T006
- T008 依赖 T001/T002，T009 依赖 T007/T008
- T010 依赖前端类型定义，T011 依赖 T006，T012 依赖 T011，T013 依赖 T011
- Phase 7 依赖所有 Phase 完成

## Notes



- 健康度评分为规则引擎（非 ML），评分规则存 `health_score_config` 表支持配置
- 聚合查询复用现有 Mapper 批量装配模式，避免 N+1
- 前端评分色标使用 antd Tag（红/黄/绿），跟进按钮复用现有跟进流程
