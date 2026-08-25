# Implementation Plan: 客户自助门户模块

**Branch**: `050-customer-portal` | **Date**: 2026-08-25 | **Spec**: [spec.md](./spec.md)

## Summary

提供公开客户门户接口：知识库文章浏览/搜索（PUBLISHED）、在线提交工单（手机/邮箱识别客户 + 查询码）、工单进度查询（工单号+手机/邮箱验证）。独立 PortalController，公开访问（SecurityConfig 白名单）。

## Technical Context

**Language/Version**: Java 17 / TypeScript 5（沿用）

**Primary Dependencies**: KnowledgeArticleService、TicketService、ContactMapper/CustomerMapper、SecurityConfig

**Storage**: 无新表（复用 knowledge_article / ticket / contact）

**Testing**: JUnit 5（CustomerPortalServiceTest 单元、CustomerPortalIT 集成）

**Target Platform**: Web（门户页 /portal：知识库浏览/提单/查进度）

**Project Type**: 对外能力（复用 015/005 实体与逻辑）

**Performance Goals**: 门户查询 ≤1s

**Constraints**: 门户接口公开（白名单）；草稿不可见；提单需识别客户；查进度需双验证

**Scale/Scope**: 门户流量（公开）

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | 独立 PortalController/Service | ✅ 满足 |
| 原则三：数据完整性、安全与校验 | 白名单、双验证防枚举、DTO 校验 | ✅ 满足 |
| 原则四：测试优先与质量门禁 | 测试先于实现 | ✅ 满足 |
| 原则五：简洁、可维护与可观测 | 复用既有服务、轻量 DTO | ✅ 满足 |

**结论**: 无门禁违规。

## Project Structure

```text
specs/050-customer-portal/
├── spec.md / plan.md / tasks.md / research.md / quickstart.md
├── contracts/portal.md

backend/src/main/java/com/crm/
├── dto/portal/（PortalArticleResponse/PortalTicketRequest/PortalTicketStatusResponse）
├── service/CustomerPortalService.java（知识库浏览/提单/进度查询）
├── controller/CustomerPortalController.java（/api/v1/portal/**）
└── config/SecurityConfig.java（白名单 + /portal 前端路由）

backend/src/test/java/com/crm/
├── service/CustomerPortalServiceTest.java
├── integration/CustomerPortalIT.java

frontend/src/
├── services/customerPortalService.ts + types/portal.ts
├── pages/portal/PortalHomePage.tsx（知识库浏览/搜索）
├── pages/portal/PortalTicketPage.tsx（提单）
├── pages/portal/PortalTrackPage.tsx（进度查询）
└── App.tsx（/portal 公开路由，不经过 RequireAuth）
```

**Structure Decision**: 门户独立 Service/Controller + 公开路由；提单复用 TicketService 创建但识别客户走门户逻辑。

## Complexity Tracking

无违规，本表留空。
