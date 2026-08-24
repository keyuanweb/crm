# 契约：实时通知 realtime-notify

## WebSocket 端点

**URL**: `ws://<host>/ws/notifications?token=<jwt>`

- 握手时后端解析 JWT，认证失败拒绝握手（400/403）。
- 认证成功后，该用户的全部会话可收到推送。

## 推送消息（服务端 → 客户端）

```json
{ "id": 12, "type": "TICKET_ASSIGN", "message": "新工单已分派给你：登录问题", "unreadCount": 3 }
```

- 触发时机：`NotificationService.notify()` 后（工单分派/回复、审批、预警等）。

## 客户端行为

1. 连接成功：监听消息，收到后更新角标（unreadCount）。
2. 断线：指数退避重连（1s→2s→4s→…→30s 上限），恢复后同步一次 unreadCount。
3. WebSocket 不可用：回退 REST 轮询（`GET /api/v1/notifications/unread-count`，30s）。

## 备注

- 列表与已读操作仍走 REST（`GET /api/v1/notifications`、`POST /mark-read`）。
- 同一用户多标签页均接收推送（会话集合广播）。
