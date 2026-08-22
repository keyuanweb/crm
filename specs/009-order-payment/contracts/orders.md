# 契约：订单与回款 /orders

**Base**: `/api/v1/orders`（查看/创建/编辑/回款：ADMIN + SALES；删除：仅 ADMIN）

## GET /orders

订单分页列表（FR-OP01）。

**Query**: `keyword`（订单号/标题）、`status`、`customerId`、`page`、`pageSize`。

**Response 200**

```json
{
  "items": [
    { "id": 1, "orderNo": "SO-20260822-0001", "title": "CRM 采购订单", "customerId": 2,
      "customerName": "Acme 科技", "contractId": 5, "amount": 1470000,
      "status": "PARTIAL", "version": 0, "createdAt": "2026-08-22T10:00:00" }
  ],
  "total": 1, "page": 1, "pageSize": 20
}
```

## GET /orders/{id}

订单详情（含回款计划台账与回款记录）。

**Response 200**

```json
{
  "id": 1, "orderNo": "SO-20260822-0001", "title": "CRM 采购订单",
  "customerId": 2, "customerName": "Acme 科技", "contractId": 5,
  "amount": 1470000, "status": "PARTIAL", "description": null,
  "paidAmount": 500000,
  "plans": [
    { "id": 10, "seqNo": 1, "amount": 500000, "dueDate": "2026-09-01",
      "description": "首付", "status": "PAID",
      "receivedAmount": 500000, "unpaidAmount": 0, "reminderStatus": "PAID", "overdueDays": null },
    { "id": 11, "seqNo": 2, "amount": 970000, "dueDate": "2026-10-01",
      "description": "尾款", "status": "PENDING",
      "receivedAmount": 0, "unpaidAmount": 970000, "reminderStatus": "NORMAL", "overdueDays": null }
  ],
  "payments": [
    { "id": 20, "planId": 10, "amount": 500000, "paidAt": "2026-08-20",
      "method": "TRANSFER", "recordedBy": 1, "createdAt": "2026-08-22T10:00:00" }
  ],
  "version": 0, "createdAt": "2026-08-22T10:00:00"
}
```

## POST /orders

创建订单（FR-OP02）。基于合同（可选）：`contractId` 存在且 EFFECTIVE，自动带入客户与金额。

**Body**:

```json
{
  "title": "CRM 采购订单", "customerId": 2, "contractId": 5,
  "amount": 1470000, "description": null,
  "plans": [
    { "amount": 500000, "dueDate": "2026-09-01", "description": "首付" },
    { "amount": 970000, "dueDate": "2026-10-01", "description": "尾款" }
  ]
}
```

**Response 201**: 详情结构（订单号自动生成；plans 为空时自动一期）。

**校验**: title 必填；customerId 必填且客户存在；contractId 若填须存在且状态 EFFECTIVE（否则 409 CONTRACT_INVALID_STATE / 400 CONTRACT_NOT_EFFECTIVE）；amount ≥0；plans 非空时金额合计必须=amount（400 PLAN_AMOUNT_MISMATCH）；dueDate 必填；每期 amount >0。

## PUT /orders/{id}

编辑订单（FR-OP04）。重建期次：已回款期次不可删除（400 PAYMENT_EXISTS）。

**Body**: 同 POST（含 `version`）。

**Response 200**。

**错误**: 404 ORDER_NOT_FOUND / 409 VERSION_CONFLICT / 400 PLAN_AMOUNT_MISMATCH / 400 PAYMENT_EXISTS。

## DELETE /orders/{id}（ADMIN）

逻辑删除订单（FR-OP09）。

**错误**: 404 / 409 ORDER_HAS_PAYMENTS（存在回款记录）。

## POST /orders/{id}/payments

登记回款（FR-OP05，FR-OP06）。

**Body**:

```json
{ "planId": 10, "amount": 500000, "paidAt": "2026-08-20", "method": "TRANSFER" }
```

**Response 200**: 详情结构（该期与订单状态已更新）。

**校验**: planId 属于该订单（404 PLAN_NOT_FOUND）；amount >0（400）；该期累计回款 + amount ≤ 该期应收（400 PAYMENT_EXCEEDS）。

## GET /orders/reminder-summary

回款提醒汇总（可选，FR-OP07 衍生）：逾期与临期期次数。

**Response 200**

```json
{ "overdueCount": 2, "dueSoonCount": 1 }
```

## 错误码汇总

| code | status | 含义 |
|---|---|---|
| ORDER_NOT_FOUND | 404 | 订单不存在 |
| CONTRACT_NOT_EFFECTIVE | 400 | 关联合同未生效 |
| PLAN_NOT_FOUND | 404 | 回款计划期次不存在 |
| PLAN_AMOUNT_MISMATCH | 400 | 期次金额合计与订单金额不一致 |
| PAYMENT_EXISTS | 400 | 存在已回款期次，不可删除 |
| PAYMENT_EXCEEDS | 400 | 回款金额超过该期应收 |
| ORDER_HAS_PAYMENTS | 409 | 存在回款记录，不可删除 |
| VERSION_CONFLICT | 409 | 乐观锁冲突 |

## 权限矩阵

| 端点 | ADMIN | SALES | SUPPORT |
|---|---|---|---|
| GET /orders、GET /orders/{id} | ✅ | ✅ | ❌ |
| POST/PUT /orders、POST /orders/{id}/payments | ✅ | ✅ | ❌ |
| DELETE /orders | ✅ | ❌ | ❌ |
