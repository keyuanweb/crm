# 契约：邮件营销

**Base**: `/api/v1/email-templates`、`/api/v1/email-campaigns`（email:manage）

## GET /email-templates?category=

模板列表：`{ "items": [ { "id":1, "name":"客户欢迎", "subject":"欢迎 {name}", "content":"<p>Hi {name}</p>", "category":"WELCOME" } ], "total": n }`

## POST /email-templates

创建模板。**Body**: `{ "name":"...", "subject":"欢迎 {name}", "content":"<html>", "category":"WELCOME" }`

## PUT /email-templates/{id} / DELETE /email-templates/{id}

编辑/删除。

## POST /email-campaigns

创建并发送群发。**Body**:
```json
{
  "name": "老客户召回",
  "templateId": 1,
  "sourceType": "SEGMENT",
  "segmentId": 3
}
```
或 `{ "name": "...", "templateId": 1, "sourceType": "CUSTOMER_IDS", "customerIds": [1,2,3] }`

**Response**: `{ "id": 5, "totalCount": 12, "status": "RUNNING" }`（异步发送，轮询状态）

## POST /email-campaigns/{id}/test

测试发送：**Body** `{ "email": "me@example.com" }`（渲染模板发给自己）。

## GET /email-campaigns

活动列表（含 sent/failed/open/click 统计）。

## GET /email-campaigns/{id}

活动详情（+发送记录分页）。

## 公开追踪端点（无 JWT，防爬频控）

- `GET /api/v1/public/track/open/{sendLogId}` → 1x1 GIF + 记录 OPEN
- `GET /api/v1/public/track/click/{sendLogId}?url=<encoded>` → 302 跳转 + 记录 CLICK

## 备注

- 收件人：SEGMENT（复用 031 细分成员）或 CUSTOMER_IDS；邮箱空跳过。
- 变量：{name}/{company}/{phone}。
- 权限 email:manage 入 028 字典。
