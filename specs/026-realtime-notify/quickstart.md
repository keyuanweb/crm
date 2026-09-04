# Quickstart: 实时通知推送

## 后端

1. pom.xml 加 `spring-boot-starter-websocket`。
2. `NotificationWebSocketHandler`：会话映射 + notifyUser + 断开清理。
3. `WebSocketConfig`：注册 `/ws/notifications` + JWT 握手拦截器。
4. `NotificationService.notify()` 后触发推送。
5. 测试：handler/推送触发单元测试 + 集成（通知产生→推送 payload 正确）。

## 前端

1. `useNotificationSocket` hook：连接/监听/重连/降级。
2. `NotificationCenter`：用 hook 即时更新角标（保留轮询降级）。

## 验证

- 后端：`mvn test`（新增测试，不影响既有 237）。
- 前端：`pnpm run typecheck` + `lint` + `test`。
- 手动：双账号，A 触发通知，B 不受影响；断网重连恢复。
