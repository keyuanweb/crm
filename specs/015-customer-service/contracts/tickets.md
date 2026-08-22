# 契约：客户服务 - 工单 /tickets

**Base**: `/api/v1/tickets`（写：ADMIN + SUPPORT；查看：ADMIN/SUPPORT 全部，SALES 仅自身客户）

## GET /tickets

工单分页列表（FR-C01/C02）。

**Query**: `keyword`（标题/描述）、`status`、`priority`、`assigneeId`、`customerId`、`page`、`pageSize`。

**Response 200**

```json
{
  "items": [
    { "id": 1, "customerId": 3, "customerName": "客户A", "contactId": null,
      "title": "登录失败", "description": "...", "priority": "HIGH",
      "status": "IN_PROGRESS", "assigneeId": 5, "assigneeName": "客服小张",
      "slaRespondDeadline": "2026-08-22T12:00:00", "slaResolveDeadline": "2026-08-23T12:00:00",
      "slaStatus": "NORMAL", "replyCount": 2, "version": 0, "createdAt": "2026-08-22T08:00:00" }
  ],
  "total": 1, "page": 1, "pageSize": 20
}
```

## GET /tickets/{id}

工单详情（SLA 状态按需刷新落库；含客户名/处理人名/回复数）。

## GET /tickets/{id}/replies

工单回复时间线（分页）。**Query**: `page`、`pageSize`。

**Response 200**

```json
{
  "items": [
    { "id": 1, "ticketId": 1, "replierId": 5, "replierName": "客服小张",
      "content": "已联系客户", "createdAt": "2026-08-22T09:00:00" }
  ],
  "total": 1, "page": 1, "pageSize": 20
}
```

## POST /tickets

创建工单（FR-C01）。创建时按优先级自动计算 SLA 到期时间。

**Body**:

```json
{ "customerId": 3, "contactId": null, "title": "登录失败",
  "description": "用户无法登录", "priority": "HIGH", "assigneeId": 5 }
```

**Response 201**: 工单结构（status 默认 OPEN）。

**校验**: customerId 必填且客户存在；title 必填 ≤200；priority ∈ 枚举；assigneeId 存在（可选）。

## PUT /tickets/{id}

编辑工单（标题/描述/优先级/备注，含 version）。

## POST /tickets/{id}/assign

分配处理人。**Body**: `{ "assigneeId": 5 }`。仅 ADMIN/SUPPORT。

## POST /tickets/{id}/reply

追加回复（FR-C04）。**Body**: `{ "content": "已联系客户..." }`（content 必填）。**Response 200**: 回复结构（replierName 为当前用户名）。

## POST /tickets/{id}/transition

状态流转（FR-C03）。**Body**: `{ "targetStatus": "IN_PROGRESS" }`。

**流转规则**: OPEN→IN_PROGRESS→RESOLVED→CLOSED；非法流转 → 409 TICKET_INVALID_STATE。

## DELETE /tickets/{id}

逻辑删除（FR-C16）。

## 错误码

| code | status | 含义 |
|---|---|---|
| TICKET_NOT_FOUND | 404 | 工单不存在 |
| TICKET_INVALID_STATE | 409 | 状态流转非法 |
| TICKET_CUSTOMER_REQUIRED | 422 | 未关联客户 |
| TICKET_FORBIDDEN | 403 | 无权限（SALES 非自身客户工单） |
