# Research: 实时通知推送

## R1 WebSocket 端点与认证

**决策**: 端点 `/ws/notifications`，JWT 经 query 参数 `?token=xxx` 传递（避免握手 Header 在部分代理下的兼容问题）。`WebSocketConfig` 注册 handler + 握手拦截器（解析 token → SecurityContext 或 userId 存 session attributes；失败拒绝握手）。

## R2 会话映射

**决策**: `ConcurrentHashMap<Long, Set<WebSocketSession>>`（userId → 会话集合，支持多标签页）。afterConnectionEstablished 加入，afterConnectionClosed 移除；连接断开时清理空 Set。

## R3 推送触发

**决策**: `NotificationService.notify()` 在 insert 后调用 `NotificationWebSocketHandler.notifyUser(userId, payload)`。payload = { id, type, message, unreadCount }（unreadCount 由 NotificationService 计算）。

## R4 前端 hook

**决策**: `useNotificationSocket(onMessage)`：
- 连接 `ws://host/ws/notifications?token=...`
- onmessage 解析 JSON → 回调（更新角标）
- onclose → 指数退避重连（1s→2s→4s→…→30s 上限）
- 连接失败（onerror + 未成功 open）→ 启用轮询降级（setInterval 30s fetchUnreadCount）
- 卸载时清理

## R5 NotificationCenter 改造

**决策**: 保留现有轮询作为降级；优先用 hook 即时更新。收到推送消息 → setUnread(payload.unreadCount) + 可选提示。
