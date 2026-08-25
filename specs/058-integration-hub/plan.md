# Implementation Plan: 集成中心模块

**Branch**: `058-integration-hub` | **Date**: 2026-08-25 | **Spec**: [spec.md](./spec.md)

## Summary

新增 `integration_channel` 表（类型/名称/URL/启用）。IntegrationChannelService（CRUD）+ 事件推送（复用 055 WebhookService.publish，通道作为目标）。业务事件发布点：TicketService.assign、LeadService.create、ApprovalEngineService.start。

## Technical Context

**Language/Version**: Java 17 / TypeScript 5（沿用）

**Primary Dependencies**: WebhookService（055）、TicketService/LeadService/ApprovalEngineService

**Storage**: MySQL 新增 `integration_channel` 表（V66）；推送记录复用 webhook_delivery

**Testing**: JUnit 5（IntegrationChannelServiceTest 单元、IntegrationHubIT 集成）

**Target Platform**: Web（集成中心页：通道管理 + 推送记录）

**Project Type**: 平台能力（新增，复用 055）

**Performance Goals**: 推送异步不阻塞主流程

**Constraints**: 通道配置仅 ADMIN；URL 校验 http/https；推送复用 055 重试

**Scale/Scope**: 通道 ≤ 数十

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | 独立通道服务 + 事件发布 | ✅ 满足 |
| 原则三：数据完整性、安全与校验 | URL 校验、仅 ADMIN | ✅ 满足 |
| 原则四：测试优先与质量门禁 | 测试先于实现 | ✅ 满足 |
| 原则五：简洁、可维护与可观测 | 复用 055 推送/记录 | ✅ 满足 |

**结论**: 无门禁违规。

## Project Structure

```text
specs/058-integration-hub/
├── spec.md / plan.md / tasks.md / research.md / data-model.md / quickstart.md
├── contracts/integration-hub.md

backend/src/main/java/com/crm/
├── entity/IntegrationChannel.java + repository/IntegrationChannelMapper.java
├── dto/integration/（ChannelRequest/Response/DeliveryView）
├── service/IntegrationChannelService.java（CRUD + publish(eventType, title)）
├── controller/IntegrationChannelController.java（/integration-channels + 推送记录）
├── service/TicketService.java（assign 发布 TICKET_ASSIGNED）
├── service/LeadService.java（create 发布 LEAD_CREATED，复用 055 处扩展）
├── service/ApprovalEngineService.java（start 发布 APPROVAL_PENDING）
├── common/ErrorCode.java（新增 INTEGRATION_* 错误码）
└── resources/db/migration/V66__integration_channel.sql

backend/src/test/java/com/crm/
├── service/IntegrationChannelServiceTest.java
├── integration/IntegrationHubIT.java

frontend/src/
├── services/integrationService.ts + types/integration.ts
├── pages/settings/IntegrationHubPage.tsx（通道管理 + 推送记录）
└── App.tsx（流程与配置组点亮"集成中心"占位项 → 路由）
```

**Structure Decision**: IntegrationChannelService.publish 内部调 WebhookService（每条启用通道按其 URL 推送）；推送记录查 webhook_delivery。

## Complexity Tracking

无违规，本表留空。
