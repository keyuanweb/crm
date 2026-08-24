# Implementation Plan: 邮件营销触达

**Branch**: `030-email-marketing` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

## Summary

新增邮件营销：`email_template`（模板）+ `email_campaign`（发送批次）+ `email_send_log`（每封状态）+ `email_track`（打开/点击事件）；`EmailTemplateService`（模板 CRUD + 变量渲染）+ `EmailCampaignService`（批量群发异步队列 + 测试发送 + 状态跟踪）；`EmailTrackController`（追踪像素 + 链接重定向 + 统计）；SMTP 配置（无 SMTP dev 模拟）；前端邮件模板管理页 + 营销活动群发页（收件人来自客户细分/标签/筛选）。

## Technical Context

**Language/Version**: Java 17（后端）+ TypeScript/React 18（前端）

**Primary Dependencies**: Spring Boot Mail（JavaMailSender）、MyBatis-Plus、Thymeleaf 或简单 String 模板（变量替换）、antd 5

**Storage**: 4 表 + Flyway V48；SMTP 配置 application.yml 环境变量

**Testing**: JUnit 5 + Mockito（模板/群发服务）、集成（EmailIT：模板 CRUD/群发状态/追踪端点）、前端（页面渲染）

**Target Platform**: Web

**Project Type**: Web 应用

**Performance Goals**: 群发异步队列（不阻塞主线程）；单批 ≤ 500

**Constraints**: 无 SMTP dev 降级（发送标记 SENT 模拟）；追踪像素/重定向自研；收件人限可见数据范围

**Scale/Scope**: 4 表 + 1 迁移 + 2 Service + 2 Controller + 前端模板/群发页

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则一：契约优先的 API 设计 | 端点契约先于实现 | ✅ 满足（contracts/email-marketing.md） |
| 原则二：分层架构与关注点分离 | Controller→Service→Repository | ✅ 满足（Template/Campaign 分离） |
| 原则三：数据完整性、安全与校验 | 服务端权限 | ✅ 满足（email:manage + @RequirePermission；追踪端点公开但轻量） |
| 原则四：测试优先与质量门禁 | 测试先于实现 | ✅ 满足（EmailTemplateServiceTest/EmailCampaignServiceTest/EmailIT + 前端测试） |
| 原则五：简洁、可维护与可观测 | 避免过度设计 | ✅ 满足（不引营销平台，自研像素/重定向） |

**结论**: 无门禁违规。

## Project Structure

### Source Code

```text
backend/src/main/java/com/crm/
├── entity/EmailTemplate.java / EmailCampaign.java / EmailSendLog.java / EmailTrack.java
├── repository/EmailTemplateMapper.java / EmailCampaignMapper.java / EmailSendLogMapper.java / EmailTrackMapper.java
├── dto/email/EmailTemplateRequest.java / EmailTemplateResponse.java / CampaignRequest.java / CampaignResponse.java
├── service/EmailTemplateService.java    # 模板 CRUD + 变量渲染（{name}/{company}/{phone}）
├── service/EmailCampaignService.java    # 群发（收件人来源：细分/标签/客户列表）+ 异步队列 + 测试发送 + 状态
├── controller/EmailController.java      # /api/v1/email-templates + /api/v1/email-campaigns
├── controller/EmailTrackController.java # 公开：/api/v1/public/track/open/{id} + /api/v1/public/track/click/{id}?url=
└── config/MailConfig.java               # JavaMailSender（SMTP 环境变量）

backend/src/main/resources/db/migration/V48__email_marketing.sql
backend/src/test/java/com/crm/
├── service/EmailTemplateServiceTest.java
├── service/EmailCampaignServiceTest.java
└── integration/EmailIT.java

frontend/src/
├── types/email.ts / services/emailService.ts
├── pages/marketing/EmailTemplatePage.tsx   # 模板管理（营销分组）
├── pages/marketing/EmailCampaignPage.tsx   # 群发页（模板+收件人细分+发送）
└── App.tsx                                 # 注册路由（营销分组）
```

**Structure Decision**: 群发用 @Async + 简单队列（线程池）逐封发送（无 SMTP dev 时标记 SENT）；变量渲染用简单 String.replace（防注入，不引模板引擎）；追踪像素为 1x1 GIF + 记录事件；点击重定向 302 到目标 URL + 记录。收件人来源复用 031 SegmentService 细分成员 + 客户列表筛选（客户邮箱）。

## Complexity Tracking

> 无违规，本表留空。
