# Implementation Plan: 订单与回款模块

**Branch**: `009-order-payment` | **Date**: 2026-08-22 | **Spec**: [spec.md](./spec.md)

## Summary

新增订单（SalesOrder）、回款计划（PaymentPlan）、回款记录（PaymentRecord）三大实体：订单支持基于已生效合同创建（自动带入客户/金额）或直接创建；回款计划（分期，期次金额合计=订单金额，未设分期自动一期）；回款登记驱动期次/订单状态（PENDING/PARTIAL/PAID）自动流转；应收账款台账按订单聚合期次（应收/已收/未收/状态/逾期天数），标识逾期与临期（3 天）并支持筛选。前端新增订单列表/详情页（含回款计划与台账）。

## Technical Context

**Language/Version**: Java 17 / TypeScript 5（沿用既有技术栈）

**Primary Dependencies**: MyBatis-Plus、Spring Validation、AuditService

**Storage**: MySQL 新增 `sales_order`、`payment_plan`、`payment_record` 三表（Flyway V19~V21）

**Testing**: JUnit 5 + Spring Boot Test（SalesOrderServiceTest/PaymentServiceTest 单元、OrderPaymentIT 集成）

**Target Platform**: Web（订单列表/详情页）

**Project Type**: 现有 Web 应用新增模块

**Performance Goals**: 订单列表/创建 ≤1s（SC-OP01）

**Constraints**: 基于合同创建须合同生效；期次金额合计=订单金额（400）；单期回款不超额（400）；回款驱动状态自动流转；订单号唯一自动生成；删除仅 ADMIN 且有关联回款不可删（409）

**Scale/Scope**: 订单/期次/回款数量级 ≤ 数千

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | Controller→Service→持久层 | ✅ 满足（复用既有分层） |
| 原则三：数据完整性、安全与校验 | DTO 校验、服务端授权、事务 | ✅ 满足（Bean Validation + @PreAuthorize + 回款事务） |
| 原则四：测试优先与质量门禁 | 测试先于实现、合并门禁 | ✅ 满足（单元/集成测试 + 既有门禁） |
| 原则五：简洁、可维护与可观测 | 无 N+1、审计 | ✅ 满足（台账批量装配 + AuditService） |

**结论**: 无门禁违规。

## Project Structure

```text
specs/009-order-payment/
├── spec.md / plan.md / tasks.md / research.md / data-model.md / quickstart.md
├── contracts/orders.md

backend/src/main/java/com/crm/
├── entity/SalesOrder.java + repository/SalesOrderMapper.java
├── entity/PaymentPlan.java + repository/PaymentPlanMapper.java
├── entity/PaymentRecord.java + repository/PaymentRecordMapper.java
├── dto/order/（OrderRequest/OrderResponse/PlanItemRequest/PlanItemResponse/PaymentRequest/PaymentRecordResponse）
├── service/SalesOrderService.java（订单 CRUD/编号生成/基于合同创建/期次重建）
├── service/PaymentService.java（回款登记/状态驱动/台账聚合/逾期临期标识）
├── controller/OrderController.java + PaymentController.java（或并入 OrderController）
├── common/ErrorCode.java（新增 ORDER_* / PAYMENT_* 错误码）
└── resources/db/migration/V19__sales_order.sql + V20__payment_plan.sql + V21__payment_record.sql

backend/src/test/java/com/crm/
├── service/SalesOrderServiceTest.java + service/PaymentServiceTest.java
├── integration/OrderPaymentIT.java

frontend/src/
├── types/order.ts + services/orderService.ts
├── pages/orders/OrderListPage.tsx + pages/orders/OrderDetailPage.tsx
└── App.tsx（订单菜单 + 路由）
```

**Structure Decision**: 沿用既有前后端目录结构（Option 2 Web 应用）。订单主体与回款/台账拆两个 Service（SalesOrderService + PaymentService）保持关注点分离。

## Complexity Tracking

无违规，本表留空。
