# Research: 集成中心模块

**Branch**: `058-integration-hub` | **Date**: 2026-08-25

## 1. 通道模型

**Decision**: `integration_channel` 表（channel_type WECHAT_WORK/DINGTALK/CUSTOM、name、webhook_url、enabled）。URL 校验 http/https。

**Rationale**: Webhook 型通道是最低侵入的 IM 接入（企业微信/钉钉群机器人均提供 Webhook）；自定义通道兼容任意 JSON 接收端。

**Alternatives considered**: OAuth 应用接入（复杂，需企业应用凭证管理）；邮件通道（已有 030）。Webhook 型 v1 最务实。

## 2. 推送复用

**Decision**: IntegrationChannelService.publish(eventType, title) 遍历启用通道，逐条调 WebhookService 的推送（构造临时订阅语义，用通道 URL + 随机 secret 签名，推送记录入 webhook_delivery）。业务事件发布点：TicketService.assign、LeadService.create（复用已有 055 发布处）、ApprovalEngineService.start。

**Rationale**: 复用 055 异步推送/退避重试/记录机制；事件发布点与 055 平行扩展。

**Alternatives considered**: 独立推送实现——重复 055；直接复用 WebhookSubscription——需为每通道建订阅，通道 CRUD 需同步订阅（耦合）。IntegrationChannelService 内部构造推送最简。
