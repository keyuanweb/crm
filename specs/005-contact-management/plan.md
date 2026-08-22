# Implementation Plan: 联系人管理模块

**Branch**: `005-contact-management` | **Date**: 2026-08-22 | **Spec**: [spec.md](./spec.md)

## Summary

新增独立联系人实体（Contact），客户下可维护多个联系人（一对多）。支持联系人 CRUD、关键字搜索/客户/角色筛选、详情（含所属客户）、逻辑删除与唯一性约束；客户详情页集成联系人列表。完全复用既有分层与认证框架。

## Technical Context

**Language/Version**: Java 17 / TypeScript 5（沿用既有技术栈）

**Primary Dependencies**: MyBatis-Plus、Spring Validation、AuditService（审计，FR-017 一致性）

**Storage**: MySQL 新增 `contact` 表（Flyway V10 迁移）

**Testing**: JUnit 5 + Spring Boot Test（ContactServiceTest 单元、ContactIT 集成）

**Target Platform**: Web（前端 ProTable 页面 + 客户详情集成）

**Project Type**: 现有 Web 应用新增模块

**Performance Goals**: 联系人列表分页 ≤1s（1000 条规模，SC-C03）

**Constraints**: 必须关联未删除客户；同客户（姓名+电话）唯一；逻辑删除；角色枚举固定

**Scale/Scope**: 联系人数量为客户规模 × 若干（≤ 数千）

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | Controller→Service→持久层 | ✅ 满足（复用既有分层） |
| 原则三：数据完整性、安全与校验 | DTO 校验、服务端授权、唯一性 | ✅ 满足（Bean Validation + 同客户唯一校验） |
| 原则四：测试优先与质量门禁 | 测试先于实现、合并门禁 | ✅ 满足（单元/集成测试 + 既有门禁） |
| 原则五：简洁、可维护与可观测 | 无 N+1、审计 | ✅ 满足（列表批量装配 + AuditService） |

**结论**: 无门禁违规。

## Project Structure

```text
specs/005-contact-management/
├── spec.md / plan.md / tasks.md

backend/src/main/java/com/crm/
├── entity/Contact.java + repository/ContactMapper.java
├── dto/contact/ContactRequest.java + ContactResponse.java
├── service/ContactService.java + controller/ContactController.java
└── resources/db/migration/V10__contact.sql
（CustomerService.detail 增加 contacts 列表）

frontend/src/
├── types/contact.ts + services/contactService.ts
├── pages/contacts/ContactListPage.tsx
├── pages/customers/CustomerDetailPage.tsx（联系人卡片）
└── App.tsx（联系人菜单 + 路由）
```

## Complexity Tracking

无违规，本表留空。
