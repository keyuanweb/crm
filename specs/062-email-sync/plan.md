# Implementation Plan: 邮件账户与同步记录模块

**Branch**: `062-email-sync` | **Date**: 2026-08-25 | **Spec**: [spec.md](./spec.md)

## Summary

新增 `mail_account`（邮箱/显示名/IMAP/SMTP/启用/默认发件）与 `mail_sync_record`（账户/方向/主题/收发件人/状态/时间/外部 id）两表。MailAccountService（CRUD/默认唯一/邮箱校验）+ MailSyncRecordService（列表/模拟同步/删除）。

## Technical Context

**Language/Version**: Java 17 / TypeScript 5（沿用）

**Primary Dependencies**: MyBatis-Plus

**Storage**: MySQL 新增 `mail_account` + `mail_sync_record` 表（V69）

**Testing**: JUnit 5（MailAccountServiceTest/MailSyncRecordServiceTest 单元、EmailSyncIT 集成）

**Target Platform**: Web（邮件账户页 + 同步记录页）

**Project Type**: 平台能力（新增）

**Performance Goals**: CRUD/模拟同步 ≤50ms

**Constraints**: 账户配置仅 ADMIN；邮箱格式校验；默认发件唯一

**Scale/Scope**: 账户 ≤ 数十；记录 ≤ 数千

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | 独立服务 | ✅ 满足 |
| 原则三：数据完整性、安全与校验 | 邮箱校验/默认唯一 | ✅ 满足 |
| 原则四：测试优先与质量门禁 | 测试先于实现 | ✅ 满足 |

**结论**: 无门禁违规。

## Project Structure

```text
specs/062-email-sync/
├── spec.md / plan.md / tasks.md / research.md / data-model.md / quickstart.md
├── contracts/email-sync.md

backend/src/main/java/com/crm/
├── entity/（MailAccount/MailSyncRecord）+ repository/（2 Mapper）
├── dto/mail/（AccountRequest/Response/SyncRecordResponse）
├── service/MailAccountService.java（CRUD/默认唯一/邮箱校验）
├── service/MailSyncRecordService.java（列表/模拟同步/删除）
├── controller/MailAccountController.java（/mail-accounts + /mail-accounts/{id}/sync + /mail-accounts/{id}/records）
├── common/ErrorCode.java（新增 MAIL_* 错误码）
└── resources/db/migration/V69__mail_sync.sql

backend/src/test/java/com/crm/
├── service/MailAccountServiceTest.java + MailSyncRecordServiceTest.java
├── integration/EmailSyncIT.java

frontend/src/
├── services/mailService.ts + types/mail.ts
├── pages/mail/MailAccountPage.tsx（账户配置）
├── pages/mail/MailSyncRecordPage.tsx（同步记录 + 模拟同步）
└── App.tsx（工作台组新增"邮件同步"菜单 → 路由）
```

**Structure Decision**: 双服务（账户与同步记录分离）；模拟同步生成 INBOUND 记录验证链路。

## Complexity Tracking

无违规，本表留空。
