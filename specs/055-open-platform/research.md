# Research: 开放平台模块

**Branch**: `055-open-platform` | **Date**: 2026-08-25

## 1. API Key 模型

**Decision**: `api_key` 表（name、key_hash、key_prefix、scopes JSON、expires_at、status ACTIVE/REVOKED、last_used_at、use_count）。创建时返回完整 key 一次；列表仅显前缀。`ApiKeyAuthFilter` 在 `/api/v1/open/**` 上校验 `X-API-Key`（查哈希 → 生效校验 → 更新 last_used）。启用 Key 内存缓存（TTL 60s）减查询。

**Rationale**: 哈希存储防泄露（完整 key 不可恢复）；前缀展示便于识别；过滤器复用安全链。

## 2. Webhook 模型

**Decision**: `webhook_subscription`（event_type、callback_url、secret、enabled）+ `webhook_delivery`（subscription_id、event、payload、status、http_status、error、retry_count）。业务事件发布点调 WebhookService.publish(eventType, entityId, payload) → 异步 POST（RestTemplate）→ HMAC-SHA256(secret, body) 放 X-Signature 头 → 失败重试 ≤3 次退避（1s/5s/30s 简化同步重试）→ 记录。

**Rationale**: HMAC 签名让回调方可验签（防伪造）；重试+记录保证可靠性可观测。

**Alternatives considered**: 消息队列（RabbitMQ/Kafka）——基础设施重，v1 线程池+重试足够。

## 3. 事件发布点

**Decision**: v1 事件：LEAD_CREATED（LeadService.create 后）、LEAD_UPDATED（update 后）、CUSTOMER_CREATED（CustomerService.create 后）。在既有 Service 加 webhookService.publish 调用（与 WorkflowEventPublisher 平行）。

**Rationale**: 复用既有业务动作触发；事件子集满足集成验证。
