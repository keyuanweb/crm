# Data Model: 实时通知推送

## NotificationPushPayload（WebSocket 消息）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 通知 id |
| type | String | WORKFLOW / TICKET_ASSIGN / TICKET_REPLY 等 |
| message | String | 通知内容 |
| unreadCount | long | 推送时未读计数 |

## 会话映射（内存）

| 结构 | 说明 |
|---|---|
| ConcurrentHashMap<Long, Set<WebSocketSession>> | userId → 会话集合（多标签页） |

## 约束

- 消息轻量 JSON；列表/已读操作仍走 REST。
- 单实例内存映射；多实例需外部广播（本期不做）。
