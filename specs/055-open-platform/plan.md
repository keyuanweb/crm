# Implementation Plan: 开放平台模块

**Branch**: `055-open-platform` | **Date**: 2026-08-25 | **Spec**: [spec.md](./spec.md)

## Summary

新增 API Key 管理（生成/哈希存储/吊销/X-API-Key 鉴权）与 Webhook 订阅（事件推送 + HMAC 签名 + 重试 + 记录）。开放端点 `/api/v1/open/**` 用 API Key 鉴权（独立过滤器加入安全链）。

## Technical Context

**Language/Version**: Java 17 / TypeScript 5（沿用）

**Primary Dependencies**: SecurityConfig/JwtAuthFilter、Spring Async、RestTemplate/WebClient、HMAC

**Storage**: MySQL 新增 `api_key`、`webhook_subscription`、`webhook_delivery` 三表（V63）

**Testing**: JUnit 5（ApiKeyServiceTest/WebhookServiceTest 单元、OpenPlatformIT 集成）

**Target Platform**: Web（API Key 管理页 + Webhook 订阅页 + 推送记录）

**Project Type**: 平台能力（新增）

**Performance Goals**: API Key 鉴权 ≤5ms（缓存）；Webhook 异步推送不阻塞主流程

**Constraints**: 管理仅 ADMIN；开放端点 v1 子集；Key 存哈希；推送重试 ≤3 次

**Scale/Scope**: Key ≤ 数十；Webhook ≤ 数十；推送记录 ≤ 数千

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | 独立过滤链 + 服务层 | ✅ 满足 |
| 原则三：数据完整性、安全与校验 | Key 哈希、HMAC 签名、仅 ADMIN | ✅ 满足 |
| 原则四：测试优先与质量门禁 | 测试先于实现 | ✅ 满足 |
| 原则五：简洁、可维护与可观测 | 推送记录、失败重试 | ✅ 满足 |

**结论**: 无门禁违规。

## Project Structure

```text
specs/055-open-platform/
├── spec.md / plan.md / tasks.md / research.md / data-model.md / quickstart.md
├── contracts/open-platform.md

backend/src/main/java/com/crm/
├── entity/（ApiKey/WebhookSubscription/WebhookDelivery）+ repository/（3 Mapper）
├── dto/open/（ApiKeyRequest/Response/WebhookRequest/Response/DeliveryResponse）
├── service/ApiKeyService.java（生成/校验/吊销/使用记录）
├── service/WebhookService.java（订阅/推送/重试/记录）
├── security/ApiKeyAuthFilter.java（X-API-Key → /api/v1/open/**）
├── controller/OpenPlatformController.java（/api/v1/open/** 开放端点 + 管理端点）
├── config/SecurityConfig.java（注册 ApiKeyAuthFilter + /api/v1/open/** 规则）
├── common/ErrorCode.java（新增 OPEN_* 错误码）
└── resources/db/migration/V63__open_platform.sql

backend/src/test/java/com/crm/
├── service/ApiKeyServiceTest.java + WebhookServiceTest.java
├── integration/OpenPlatformIT.java

frontend/src/
├── services/openPlatformService.ts + types/openPlatform.ts
├── pages/open/ApiKeyPage.tsx（Key 管理）
├── pages/open/WebhookPage.tsx（订阅 + 推送记录）
└── App.tsx（系统管理组点亮"开放平台"占位项 → 路由）
```

**Structure Decision**: ApiKeyService 缓存启用 Key（内存 Map 减少查询）；WebhookService 异步推送（@Async + 线程池）+ 退避重试；ApiKeyAuthFilter 在 JwtAuthFilter 前处理 /api/v1/open/**。

## Complexity Tracking

无违规，本表留空。
