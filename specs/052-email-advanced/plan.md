# Implementation Plan: 邮件高级能力模块

**Branch**: `052-email-advanced` | **Date**: 2026-08-25 | **Spec**: [spec.md](./spec.md)

## Summary

在 030 邮件营销上扩展：退订管理（新表 email_unsubscribe + 公开退订端点 + 发信排除）、送达统计（复用 EmailSendLog/EmailTrack 聚合）、A-B 主题测试（EmailCampaign/EmailSendLog 加 variant 字段）。

## Technical Context

**Language/Version**: Java 17 / TypeScript 5（沿用）

**Primary Dependencies**: EmailCampaignService（030）、EmailTemplate、SecurityConfig

**Storage**: MySQL 新增 `email_unsubscribe` 表（V60）；email_campaign/email_send_log 加列（V60 一并）

**Testing**: JUnit 5（EmailUnsubscribeServiceTest 单元、EmailAdvancedIT 集成）

**Target Platform**: Web（退订名单页 + 群发详情统计 + 创建群发 A/B 配置）

**Project Type**: 既有模块增强（030）

**Performance Goals**: 统计查询 ≤1s

**Constraints**: 退订按邮箱匹配；群发与 049 自动化发信排除退订；统计分母为成功发送

**Scale/Scope**: 退订名单 ≤ 数千；批次统计

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | 独立退订/统计服务 | ✅ 满足 |
| 原则三：数据完整性、安全与校验 | 公开端点防滥用、DTO 校验 | ✅ 满足 |
| 原则四：测试优先与质量门禁 | 测试先于实现 | ✅ 满足 |
| 原则五：简洁、可维护与可观测 | 复用 030 实体、聚合查询 | ✅ 满足 |

**结论**: 无门禁违规。

## Project Structure

```text
specs/052-email-advanced/
├── spec.md / plan.md / tasks.md / research.md / data-model.md / quickstart.md
├── contracts/email-advanced.md

backend/src/main/java/com/crm/
├── entity/EmailUnsubscribe.java + repository/EmailUnsubscribeMapper.java
├── entity/EmailCampaign.java（+variant/subjectB/winner）+ EmailSendLog.java（+variant）
├── dto/email/（EmailUnsubscribeResponse/EmailStatsResponse/CampaignA/BRequest 扩展）
├── service/EmailUnsubscribeService.java（退订/名单/恢复/排除检查）
├── service/EmailCampaignService.java（发信排除退订 + A/B 分组 + 统计聚合）
├── controller/EmailController.java（退订管理 + 统计端点）+ EmailUnsubscribeController（公开退订）
└── resources/db/migration/V60__email_unsubscribe.sql

backend/src/test/java/com/crm/
├── service/EmailUnsubscribeServiceTest.java
├── integration/EmailAdvancedIT.java

frontend/src/
├── services/emailService.ts（扩展）+ types/email.ts
├── pages/email/EmailUnsubscribePage.tsx（退订名单）
├── 群发详情统计展示 + 创建群发 A/B 配置
```

**Structure Decision**: 退订独立 Service；统计与 A-B 扩展在 EmailCampaignService；公开退订端点白名单。

## Complexity Tracking

无违规，本表留空。
