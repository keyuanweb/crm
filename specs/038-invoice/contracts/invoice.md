# 契约：发票管理

**Base**: `/api/v1/invoices`（invoice:manage，交易数据范围）

## GET /invoices?orderId=&status=&invoiceType=&page=&pageSize=

发票列表（按订单/状态/类型筛选）。

**Response**: `{ "items": [ { "id":1, "orderId":5, "orderNo":"DD-xxx", "invoiceNo":"INV-202608-001", "title":"Acme 科技", "taxNo":"91xxx", "amount":100000, "invoiceType":"SPECIAL", "status":"ISSUED", "voidReason":null, "issuedAt":"2026-08-23T10:00", "customerName":"Acme" } ], "total": n }`

## POST /invoices

开票。**Body**: `{ "orderId":5, "title":"Acme 科技", "taxNo":"91xxx", "amount":100000, "invoiceType":"SPECIAL" }`

**Response 200**: 发票（invoiceNo 自动生成，状态 ISSUED）
**400**: 金额超订单剩余可开额 / 订单不存在

## POST /invoices/{id}/void

作废。**Body**: `{ "reason":"开票信息错误" }`（原因必填）

## GET /invoices/stats

开票统计：`{ "totalInvoiceAmount": 100000, "totalOrderAmount": 500000, "invoiceRate": 0.2, "byOrder": [ { "orderId":5, "orderNo":"DD-xxx", "invoiced":100000, "orderAmount":500000, "rate":0.2 } ] }`

## 备注

- 编号 INV-{yyyyMM}-{seq}（当日序号）。
- 累计开票（非作废）≤ 订单金额；作废释放可开额。
- 权限 invoice:manage 入 028 字典。
