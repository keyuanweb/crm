# 契约：客户自助门户 /api/v1/portal/**

**公开访问**（SecurityConfig 白名单，无需登录）。

## GET /portal/articles

知识库文章列表（仅 PUBLISHED）。

**Query**: `keyword`（标题/关键词）、`page`、`pageSize`。

**Response 200**

```json
{
  "items": [
    { "id": 1, "category": "PRODUCT_USAGE", "title": "如何创建报价单",
      "keywords": "报价,CPQ", "updatedAt": "2026-08-20T10:00:00" }
  ],
  "total": 1, "page": 1, "pageSize": 20
}
```

## GET /portal/articles/{id}

文章详情（仅 PUBLISHED）。**Response 200**: 含 content。

## POST /portal/tickets

在线提交工单。

**Body**

```json
{
  "phone": "13800000000",
  "email": "customer@test.com",
  "title": "产品使用问题",
  "description": "无法导出报表",
  "priority": "MEDIUM"
}
```

**Response 201**

```json
{ "ticketNo": "TK-20260825-0003", "status": "OPEN", "createdAt": "2026-08-25T10:00:00" }
```

**规则**: phone 或 email 至少一个；按 phone 或 email 匹配联系人识别客户（422 PORTAL_CUSTOMER_NOT_FOUND 未匹配）。

## POST /portal/tickets/status

工单进度查询（双验证防枚举）。

**Body**

```json
{ "ticketNo": "TK-20260825-0003", "phone": "13800000000", "email": "customer@test.com" }
```

**Response 200**

```json
{
  "ticketNo": "TK-20260825-0003", "status": "IN_PROGRESS", "priority": "MEDIUM",
  "createdAt": "2026-08-25T10:00:00", "slaStatus": "NORMAL",
  "replies": [ { "content": "已收到，正在处理", "createdAt": "2026-08-25T11:00:00" } ]
}
```

**规则**: ticketNo 必填；phone 或 email 至少一个；工单关联客户的联系人 phone/email 匹配才返回（404 PORTAL_TICKET_NOT_FOUND 不匹配）。

## 错误码

| code | status | 含义 |
|---|---|---|
| PORTAL_CUSTOMER_NOT_FOUND | 422 | 未匹配到客户（请先由销售注册） |
| PORTAL_TICKET_NOT_FOUND | 404 | 工单不存在或验证不匹配 |
| PORTAL_CONTACT_REQUIRED | 422 | 手机号或邮箱至少填一个 |
