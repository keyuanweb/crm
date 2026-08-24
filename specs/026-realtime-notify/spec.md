# Feature Specification: 实时通知推送

**Feature Branch**: `026-realtime-notify`

**Created**: 2026-08-23

**Status**: Draft

**Input**: User description: "实时通知推送：WebSocket 实时推送新通知（工单分派/审批/预警），前端角标即时更新替代 30 秒轮询"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 实时通知推送 (Priority: P1)

系统产生新通知（工单分派给我、审批通过/驳回、流失预警等）时，通过 WebSocket 实时推送给目标用户，前端顶栏未读角标即时更新，无需手动刷新或等待轮询。

**Why this priority**: 当前通知 30 秒轮询，实时性差且浪费请求。WebSocket 推送让"审批通过"、"工单分派"等关键事件即时触达，提升协作效率与体验。

**Independent Test**: 可独立验证——管理员创建一条分派给销售的通知，销售页面角标在 2 秒内自动更新，无需刷新。

**Acceptance Scenarios**:

1. **Given** 系统为当前用户产生一条新通知（如工单分派），**When** 用户停留在任意页面，**Then** 顶栏未读角标在 2 秒内自动 +1。
2. **Given** WebSocket 连接正常，**When** 通知产生，**Then** 收到实时推送消息（含通知内容）。
3. **Given** WebSocket 连接断开（网络波动），**When** 恢复连接，**Then** 前端自动重连并同步一次未读计数（兜底）。

### User Story 2 - WebSocket 连接管理 (Priority: P1)

后端提供基于用户身份的 WebSocket 端点（连接时携带 JWT 认证），维护用户→会话映射；断线自动清理。前端连接后监听消息，断线自动重连（指数退避）。

**Why this priority**: 稳定的连接管理是实时推送的基础——认证、会话映射、断线清理与重连缺一不可。

**Independent Test**: 可独立验证——两用户分别登录连接，A 的通知不推给 B；断线后重连成功。

**Acceptance Scenarios**:

1. **Given** 用户 A、B 均在线连接，**When** 系统为 A 产生通知，**Then** 仅 A 收到推送，B 无感知。
2. **Given** 用户连接后登出/超时，**When** 再次登录，**Then** 重新建立连接（旧会话清理）。

### User Story 3 - 推送降级（可选） (Priority: P2)

WebSocket 不可用（如后端未启用）时，前端自动回退到现有的 30 秒轮询，保证通知功能不失效。

**Why this priority**: 兼容性保障——不同部署环境（无 WebSocket 支持）下通知仍可用。

**Independent Test**: 可独立验证——WebSocket 连接失败时，角标仍通过轮询更新。

**Acceptance Scenarios**:

1. **Given** WebSocket 连接失败，**When** 页面加载，**Then** 自动启用轮询模式，角标正常更新。

### Edge Cases

- 同一用户多标签页：多个 WebSocket 连接，推送广播到该用户全部会话。
- 未认证连接：拒绝（401）。
- 连接数上限：超出限制时拒绝新连接或提示。
- 消息量控制：推送轻量消息（通知 id/type/message/unreadCount），不推送全量列表。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统必须提供 WebSocket 端点，连接时通过 JWT 认证用户身份（未认证拒绝）。
- **FR-002**: 系统必须维护 用户→WebSocket 会话 映射，支持同一用户多会话（多标签页）。
- **FR-003**: 系统必须在 `NotificationService.notify()` 后通过 WebSocket 向目标用户实时推送通知消息（含 id/type/message/unreadCount）。
- **FR-004**: 前端必须监听 WebSocket 消息，收到后即时更新顶栏未读角标（无需轮询）。
- **FR-005**: 前端必须在 WebSocket 断线时自动重连（指数退避，上限 30 秒），恢复后同步一次未读计数。
- **FR-006**: 前端必须在 WebSocket 不可用时回退到现有轮询模式（每 30 秒），保证通知不失效。
- **FR-007**: 连接断开时后端必须清理会话映射，避免内存泄漏。
- **FR-008**: 推送消息必须轻量（通知摘要 + 未读计数），不推送全量列表（列表仍走 REST）。

### Key Entities

- **WebSocket 会话管理（NotificationWebSocketHandler）**: 维护 userId → Set<Session> 映射；`notifyUser(userId, payload)` 向该用户所有会话发送。
- **推送消息（NotificationPushPayload）**: { id, type, message, unreadCount } JSON。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 通知产生到前端角标更新的延迟 ≤ 2 秒（局域网，抽查 10 次）。
- **SC-002**: 用户隔离正确率 100%——A 的通知不推给 B（抽查 10 次）。
- **SC-003**: 断线重连成功率 100%——模拟断线后 30 秒内自动恢复（抽查 5 次）。
- **SC-004**: 轮询降级正确率 100%——WebSocket 不可用时角标仍更新（抽查 5 次）。
- **SC-005**: 会话清理正确率 100%——断线后会话映射中无残留（抽查 5 次）。

## Assumptions

- 使用 Spring WebSocket（spring-boot-starter-websocket），端点 `/ws/notifications`，JWT 通过 query 参数或握手拦截器认证。
- 推送为轻量 JSON（通知 id/type/message/unreadCount），列表与已读操作仍走 REST。
- 前端用原生 WebSocket API（不引入 socket.io 等依赖，YAGNI），封装为 `useNotificationSocket` hook。
- 重连指数退避：1s→2s→4s→…→上限 30s。
- 会话映射存内存（单实例）；多实例部署需外部广播（超出本期范围，Assumptions 说明）。
