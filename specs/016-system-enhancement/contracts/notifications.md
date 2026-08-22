# 契约：系统增强 - 通知中心 /notifications

**Base**: `/api/v1/notifications`（仅本人）

## GET /notifications

通知分页列表（未读优先，可筛选类型）。

**Query**: `type`（WORKFLOW/TICKET_ASSIGN/TICKET_REPLY）、`page`、`pageSize`。

**Response 200**

```json
{
  "items": [
    { "id": 1, "type": "WORKFLOW", "message": "线索「张三」已分配给你",
      "read": false, "entityType": "LEAD", "entityId": 5, "createdAt": "2026-08-22T10:00:00" }
  ],
  "total": 1, "page": 1, "pageSize": 20
}
```

## GET /notifications/unread-count

未读计数（顶栏角标）。

**Response 200**: `{ "unreadCount": 3 }`

## POST /notifications/{id}/read

标记单条已读。

## POST /notifications/read-all

全部标记已读。**Response 200**: `{ "updated": 5 }`

## 通知产生场景

- WORKFLOW：013 工作流通知（数据迁移至 notification 表）。
- TICKET_ASSIGN：工单分配时通知新处理人。
- TICKET_REPLY：工单回复时通知处理人/创建人（本人除外）。

## 错误码

| code | status | 含义 |
|---|---|---|
| NOTIFICATION_NOT_FOUND | 404 | 通知不存在 |
