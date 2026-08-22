# Research: 订单与回款模块

**Branch**: `009-order-payment` | **Date**: 2026-08-22

## 1. 订单与合同关联

**Decision**: `POST /orders` 支持可选 `contractId`：若提供，校验合同存在且状态为 EFFECTIVE（生效中），自动带入客户与金额（合同金额）；否则客户必填、金额手填。订单号 `SO-YYYYMMDD-XXXX`（同报价/合同编号生成模式）。

**Rationale**: 合同→订单金额链路唯一可追溯（SC-OP02）；编号与既有模块一致。

## 2. 分期计划与状态机

**Decision**: 订单创建/编辑时维护期次列表（金额、计划日期、说明）；期次金额合计必须=订单金额（400）。未提供期次时自动生成一期（金额=订单金额）。编辑订单时：删除旧期次并重建，但已回款期次保留（删除会 409 PAYMENT_EXISTS）。期次状态 PENDING/PARTIAL/PAID 与订单状态 PENDING/PARTIAL/PAID 均由回款登记自动驱动（不做手工改状态）。

**Rationale**: spec 假设"编辑时已回款期次不可删除"；状态自动驱动避免人为不一致。

## 3. 回款登记与超额校验

**Decision**: `POST /orders/{id}/payments`：请求含 `planId`（必填）、`amount`（>0）、`paidAt`、`method`。事务内：读取计划累计已回款 → 新累计 ≤ 应收否则 400（PAYMENT_EXCEEDS）；插入回款记录；重算该期状态与订单整体状态（Σ已收==Σ应收→PAID）。

**Rationale**: 超额校验在事务内基于最新累计（防并发超收）；状态由累计金额推导，简单可靠。

## 4. 台账与逾期/临期标识

**Decision**: 台账 = 订单详情内嵌的期次列表，每期含：应收/已收/未收、状态、计划日期、逾期天数（逾期时）、提醒标识（OVERDUE 逾期 / DUE_SOON 临期 / NORMAL 正常 / PAID 已回款）。逾期 = 计划日期 < 今天 且 未全额回款，逾期天数 = 今天-计划日期；临期 = 计划日期 ∈ [今天, 今天+3] 且未全额回款。筛选参数 `reminderStatus`（OVERDUE/DUE_SOON/NORMAL）作用于期次级。

**Rationale**: 台账为订单详情的一部分（订单量级小，无需独立台账页）；提醒标识在 Service 层计算（Controller 不承载业务）。

## 5. 契约与权限

**Decision**: 契约写入 `contracts/orders.md`。订单查看/创建/编辑/回款 SALES+ADMIN（类级 @PreAuthorize）；删除仅 ADMIN（方法级）。回款登记接口挂在订单下（`/orders/{id}/payments`）。

**Rationale**: 与既有模块权限矩阵一致；服务端强制授权（章程原则三）。
