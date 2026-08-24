# 契约：公告与内部协作

**Base**: `/api/v1/announcements`（announcement:manage）、`/api/v1/comments`

## GET /announcements

公告列表（未过期 + 置顶优先 + 含已读状态）：`{ "items": [ { "id":1, "title":"季度目标", "content":"<p>…</p>", "pinned":true, "expiresAt":null, "read":false, "createdAt":"…" } ], "total": n }`

## POST /announcements

发布。**Body**: `{ "title":"季度目标发布", "content":"<p>…</p>", "pinned":true, "expiresAt":"2026-12-31T00:00:00" }`

## PUT /announcements/{id} / DELETE /announcements/{id}

编辑/删除。

## POST /announcements/{id}/read

标记已读（幂等）。

## GET /announcements/unread-count

未读公告数（角标）。

## GET /comments?entityType=CUSTOMER&entityId=1

评论列表（时间正序）。`{ "items": [ { "id":1, "content":"@张三 请跟进", "authorId":1, "authorName":"admin", "createdAt":"…" } ], "total": n }`

## POST /comments

发表评论。**Body**: `{ "entityType":"CUSTOMER", "entityId":1, "content":"@张三 请确认" }`（@提及 → 026 通知被提及人，跳转实体详情）

## DELETE /comments/{id}

删除（作者/管理员）。

## 备注

- 公告过期自动隐藏（查询过滤）。
- 评论实体：CUSTOMER/LEAD/OPPORTUNITY/TICKET。
- @提及：`@用户名` 匹配启用用户 → 通知。
- 权限 announcement:manage 入 028 字典。
