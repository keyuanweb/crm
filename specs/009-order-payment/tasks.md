# Tasks: 订单与回款模块

**Input**: Design documents from `/specs/009-order-payment/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2/US3/US4

---

## Phase 1: 数据模型与基础

- [x] T001 [P] 创建 Flyway 迁移 V19 in `backend/src/main/resources/db/migration/V19__sales_order.sql`（sales_order 表 + active_key 单号唯一 + 索引）
- [x] T002 [P] 创建 Flyway 迁移 V20 in `backend/src/main/resources/db/migration/V20__payment_plan.sql`（payment_plan 表）
- [x] T003 [P] 创建 Flyway 迁移 V21 in `backend/src/main/resources/db/migration/V21__payment_record.sql`（payment_record 表）
- [x] T004 [P] 创建 SalesOrder 实体/Mapper in `backend/src/main/java/com/crm/entity/SalesOrder.java` + `backend/src/main/java/com/crm/repository/SalesOrderMapper.java`
- [x] T005 [P] 创建 PaymentPlan 实体/Mapper in `backend/src/main/java/com/crm/entity/PaymentPlan.java` + `backend/src/main/java/com/crm/repository/PaymentPlanMapper.java`
- [x] T006 [P] 创建 PaymentRecord 实体/Mapper in `backend/src/main/java/com/crm/entity/PaymentRecord.java` + `backend/src/main/java/com/crm/repository/PaymentRecordMapper.java`
- [x] T007 [P] H2 测试 schema 同步三表 in `backend/src/test/resources/schema-h2.sql`
- [x] T008 [P] ErrorCode 新增 ORDER_NOT_FOUND/CONTRACT_NOT_EFFECTIVE/PLAN_NOT_FOUND/PLAN_AMOUNT_MISMATCH/PAYMENT_EXISTS/PAYMENT_EXCEEDS/ORDER_HAS_PAYMENTS in `backend/src/main/java/com/crm/common/ErrorCode.java`

**Checkpoint**: 数据模型就绪

---

## Phase 2: 用户故事 1 - 订单管理 (P0) 🎯 MVP

**Goal**: 订单创建（可直接/基于已生效合同）+ 列表 + 详情。

**Independent Test**: 基于合同创建订单→金额自动带入→列表可见→详情正确。

### 实现

- [x] T009 [P] [US1] 创建 OrderRequest/OrderResponse/PlanItemRequest/PlanItemResponse DTO in `backend/src/main/java/com/crm/dto/order/`
- [x] T010 [US1] 创建 SalesOrderService in `backend/src/main/java/com/crm/service/SalesOrderService.java`（创建/列表/详情/编号生成/基于合同校验/期次自动一期）
- [x] T011 [US1] 创建 OrderController in `backend/src/main/java/com/crm/controller/OrderController.java`（GET 列表/详情、POST、DELETE 仅 ADMIN；类级 ADMIN+SALES）
- [x] T012 [US1] 创建 SalesOrderServiceTest 单元测试 in `backend/src/test/java/com/crm/service/SalesOrderServiceTest.java`（创建/合同未生效 400/自动一期/编号生成）
- [x] T013 [US1] 创建 OrderPaymentIT 集成测试（订单部分）in `backend/src/test/java/com/crm/integration/OrderPaymentIT.java`
- [x] T014 [US1] 前端类型与服务 in `frontend/src/types/order.ts` + `frontend/src/services/orderService.ts`
- [x] T015 [US1] 订单列表页 in `frontend/src/pages/orders/OrderListPage.tsx`（ProTable + 创建弹窗含合同选择与期次编辑）

**Checkpoint**: US1 可用——订单管理完整

---

## Phase 3: 用户故事 2 - 分期回款计划 (P0)

**Goal**: 期次维护（创建/编辑）+ 金额合计校验 + 未设分期自动一期。

**Independent Test**: 创建 3 期→合计校验→编辑重建（已回款期次保留）。

### 实现

- [x] T016 [US2] SalesOrderService 增加期次校验与重建逻辑（合计=订单金额 400；编辑时已回款期次不可删除 400 PAYMENT_EXISTS）in `backend/src/main/java/com/crm/service/SalesOrderService.java`
- [x] T017 [US2] OrderController 增加 PUT /orders/{id} in `backend/src/main/java/com/crm/controller/OrderController.java`
- [x] T018 [US2] SalesOrderServiceTest 增加期次用例 in `backend/src/test/java/com/crm/service/SalesOrderServiceTest.java`（合计不匹配 400/已回款期次删除 400）
- [x] T019 [US2] 前端创建/编辑弹窗完善期次编辑器（动态增删行、合计提示）in `frontend/src/pages/orders/OrderListPage.tsx`

**Checkpoint**: US2 可用——分期计划完整

---

## Phase 4: 用户故事 3 - 回款记录与台账 (P0)

**Goal**: 回款登记 + 状态自动驱动 + 台账（应收/已收/未收）。

**Independent Test**: 登记第 1 期→PAID→台账未收减少→订单 PARTIAL；超额 400。

### 实现

- [x] T020 [US3] 创建 PaymentRequest/PaymentRecordResponse DTO in `backend/src/main/java/com/crm/dto/order/`
- [x] T021 [US3] 创建 PaymentService in `backend/src/main/java/com/crm/service/PaymentService.java`（回款登记事务/超额校验/期次与订单状态重算/台账聚合含已收未收）
- [x] T022 [US3] OrderController 增加 POST /orders/{id}/payments in `backend/src/main/java/com/crm/controller/OrderController.java`
- [x] T023 [US3] 创建 PaymentServiceTest 单元测试 in `backend/src/test/java/com/crm/service/PaymentServiceTest.java`（登记成功/超额 400/部分回款/状态重算）
- [x] T024 [US3] OrderPaymentIT 增加回款用例（登记/超额/状态流转）in `backend/src/test/java/com/crm/integration/OrderPaymentIT.java`
- [x] T025 [US3] 前端详情页 in `frontend/src/pages/orders/OrderDetailPage.tsx`（台账表 + 回款记录 + 登记回款弹窗）

**Checkpoint**: US3 可用——回款闭环

---

## Phase 5: 用户故事 4 - 回款提醒 (P1)

**Goal**: 逾期/临期标识 + 台账筛选。

**Independent Test**: 构造逾期/临期期次→标识正确→筛选正确。

### 实现

- [x] T026 [US4] PaymentService 增加提醒标识计算（OVERDUE 逾期含天数/DUE_SOON 临期 3 天/NORMAL/PAID）与台账筛选 in `backend/src/main/java/com/crm/service/PaymentService.java`
- [x] T027 [US4] OrderController 增加 GET /orders/reminder-summary in `backend/src/main/java/com/crm/controller/OrderController.java`
- [x] T028 [US4] PaymentServiceTest 增加逾期/临期边界用例 in `backend/src/test/java/com/crm/service/PaymentServiceTest.java`
- [x] T029 [US4] OrderPaymentIT 增加提醒标识用例 in `backend/src/test/java/com/crm/integration/OrderPaymentIT.java`
- [x] T030 [US4] 前端详情页台账显示逾期/临期 Tag + 列表页提醒汇总卡片 in `frontend/src/pages/orders/OrderDetailPage.tsx` + `frontend/src/pages/orders/OrderListPage.tsx`

**Checkpoint**: US4 可用——回款提醒

---

## Phase 6: 收尾与验证

- [x] T031 前端路由与菜单 in `frontend/src/App.tsx`（订单菜单 + 路由）
- [x] T032 Backend `mvn verify` 通过（含新增测试、spotless、JaCoCo）
- [x] T033 单独运行 `mvn test "-Dtest=OrderPaymentIT"` 通过
- [x] T034 Frontend typecheck / lint / test / build 通过
- [x] T035 线上端点验证（合同生效→订单创建→期次校验→回款登记→超额 400→台账提醒→删除权限 403）
- [x] T036 更新契约文档（按实现校正）与 roadmap 009 标记 `[x]`

**Checkpoint**: 模块完整可用
