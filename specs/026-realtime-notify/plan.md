# Implementation Plan: 实时通知推送

**Branch**: `026-realtime-notify` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

## Summary

引入 Spring WebSocket：新增 `NotificationWebSocketHandler`（JWT 握手认证 + userId→会话映射 + notifyUser 广播）与配置；`NotificationService.notify()` 后通过 WebSocket 推送轻量消息（id/type/message/unreadCount）；前端封装 `useNotificationSocket` hook（监听 + 断线重连指数退避 + 轮询降级），`NotificationCenter` 角标即时更新。

## Technical Context

**Language/Version**: Java 17（后端）+ TypeScript/React 18（前端）

**Primary Dependencies**: Spring Boot 3.2 + spring-boot-starter-websocket（新增依赖）、JWT（JwtUtil 已有）、React 18（原生 WebSocket API）

**Storage**: 会话映射存内存（ConcurrentHashMap<Long, Set<WebSocketSession>>）；无新表

**Testing**: JUnit 5 + Mockito（单元，handler 逻辑）、集成（可用 MockWebSocket 或跳过真实握手，测服务层通知触发推送）、前端 hook 测试（mock WebSocket）

**Target Platform**: Web（现代浏览器）

**Project Type**: Web 应用（Spring Boot 后端 + React 前端）

**Performance Goals**: 推送延迟 ≤ 2s；重连退避 1s→30s

**Constraints**: 单实例内存会话映射；前端原生 WebSocket（无 socket.io）；JWT 经握手拦截器认证

**Scale/Scope**: 1 个依赖 + 1 个 handler + 1 个配置 + NotificationService 改造 + 前端 hook + NotificationCenter 改造

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则一：契约优先的 API 设计 | 端点契约先于实现 | ✅ 满足（contracts/realtime-notify.md 定义 WS 消息契约） |
| 原则二：分层架构与关注点分离 | Controller→Service→Repository | ✅ 满足（WebSocket handler 独立，通知仍走 NotificationService） |
| 原则三：数据完整性、安全与校验 | 服务端认证 | ✅ 满足（JWT 握手认证，未认证拒绝） |
| 原则四：测试优先与质量门禁 | 测试先于实现 | ✅ 满足（HandlerTest + NotificationService 推送触发测试 + 前端 hook 测试） |
| 原则五：简洁、可维护与可观测 | YAGNI | ✅ 满足（原生 WebSocket，不引入 socket.io） |

**结论**: 无门禁违规。

## Project Structure

### Documentation (this feature)

```text
specs/026-realtime-notify/
├── plan.md / spec.md / research.md / data-model.md / quickstart.md
├── contracts/（realtime-notify 契约）
└── tasks.md
```

### Source Code (repository root)

```text
backend/
├── pom.xml                                   # 修改：+ spring-boot-starter-websocket
└── src/main/java/com/crm/
    ├── config/WebSocketConfig.java           # 新增：注册 /ws/notifications + 握手拦截器（JWT）
    ├── ws/NotificationWebSocketHandler.java  # 新增：userId→会话映射 + notifyUser + 断开清理
    ├── ws/NotificationPushPayload.java       # 新增：{id,type,message,unreadCount}
    └── service/NotificationService.java      # 修改：notify() 后调 handler.notifyUser

frontend/src/
├── hooks/useNotificationSocket.ts            # 新增：连接/监听/重连/降级
├── components/NotificationCenter.tsx         # 修改：用 hook 即时更新角标（保留轮询降级）
```

**Structure Decision**: 沿用既有分层。WebSocket 端点 `/ws/notifications?token=xxx`（JWT query 认证，避免握手 Header 兼容问题）；消息轻量 JSON；会话映射内存 ConcurrentHashMap；前端 hook 封装重连与降级。

## Complexity Tracking

> 无违规，本表留空。
