# Tasks: 实时通知推送
**Input**: Design documents from `/specs/026-realtime-notify/`





**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/realtime-notify.md



**Tests**: 后端单元测试（NotificationWebSocketHandlerTest）、集成测试（RealtimeNotifyIT）；前端 hook 测试（useNotificationSocket.test）

## Phase 1: 后端测试

- [x] T001 [P] [US1] 后端测试：编写 `backend/src/test/java/com/crm/ws/NotificationWebSocketHandlerTest.java` 单元测试，覆盖连接建立/断开、`notifyUser` 向目标用户推送、用户隔离（A 的通知不推给 B）、会话清理等场景。
- [x] T002 [P] [US1] 后端测试：编写 `backend/src/test/java/com/crm/integration/RealtimeNotifyIT.java` 集成测试，模拟通知触发后 handler 收到推送，验证 handler 通过 MockWebSocket 发送消息正确。

## Phase 2: 后端实现

- [x] T003 [P] 后端实现：修改 `pom.xml` 添加 `spring-boot-starter-websocket` 依赖。
- [x] T004 [US1] 后端实现：编写 `ws/NotificationPushPayload.java`，包含 id/type/message/unreadCount 字段。
- [x] T005 [US1] 后端实现：编写 `ws/NotificationWebSocketHandler.java`，实现：
  - 使用 `ConcurrentHashMap<Long, Set<WebSocketSession>>` 维护 userId→会话映射
  - `afterConnectionEstablished` 将 session 加入 userId 对应的 Set
  - `afterConnectionClosed` 从映射中移除 session
  - `notifyUser(userId, payload)` 向该用户所有会话发送 JSON 消息
  - 确保线程安全，避免并发修改
- [x] T006 [US1] 后端实现：编写 `config/WebSocketConfig.java`，注册 `/ws/notifications` 端点 handler，配置 JWT 握手拦截器（通过 query `token` 参数认证，使用 JwtUtil 解析 userId 设置到 session attributes），添加 CORS 配置。
- [x] T007 [US1] 后端实现：修改 `NotificationService.notify()` 方法，插入 `handler.notifyUser(userId, payload)` 调用，其中 `payload.unreadCount` 通过 `unreadCount(userId)` 获取。

## Phase 3: 前端实现

- [x] T008 [P] [US1] 前端实现：编写 `hooks/useNotificationSocket.ts`，实现：
  - 连接 `ws://host/ws/notifications?token=`（JWT query 参数）
  - 监听 `onmessage` 事件，收到通知后更新未读计数
  - 断线重连逻辑（指数退避：1s→2s→4s→...→上限 30s）
  - WebSocket 不可用时降级为 `setInterval 30s fetchUnreadCount` 轮询
- [x] T009 [US1] 前端实现：修改 `components/NotificationCenter.tsx`，使用 hook 即时更新角标（保留轮询降级），收到 WebSocket 消息后调用 `setUnread(unreadCount)` 更新状态。
- [x] T010 [P] [US1] 前端实现：编写 `hooks/useNotificationSocket.test.ts`，使用 Mock WebSocket 测试连接/重连/降级逻辑。

## Phase 4: 质量检查

- [x] T011 后端：运行 `mvn test` 确保所有后端测试（handler/集成测试）通过，覆盖率 ≥ 80%。
- [x] T012 前端：运行 `pnpm run typecheck` + `lint` + `test` 确保前端无类型错误。
- [x] T013 [P] 人工审查：检查 WebSocket 连接管理、用户隔离、断线重连、推送降级等关键场景是否符合 spec.md 要求。

## Dependencies & Execution Order


- T001/T002 可并行执行（后端测试）
- T003 先于 T004/T005 完成
- T004 先于 T006/T007 完成
- T008/T010 可并行执行（前端实现）
- T009 先于 T008 完成
- Phase 4 在所有 Phase 1-3 任务完成后执行

## Notes


- Spring WebSocket starter 是新增依赖，无需额外配置
- 使用原生 WebSocket API（YAGNI，不引入 socket.io）
- JWT 通过 query token 参数认证，避免握手 Header 兼容问题
- 推送消息为轻量 JSON（通知摘要 + 未读计数），列表与已读操作仍走 REST
- 断线重连使用指数退避（1s→30s 上限）
