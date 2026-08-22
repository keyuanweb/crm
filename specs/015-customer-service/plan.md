# Implementation Plan: 客户服务模块

**Branch**: `015-customer-service` | **Date**: 2026-08-22 | **Spec**: [spec.md](./spec.md)

## Summary

新增客户服务模块：工单（Ticket）管理（关联客户/联系人、优先级、状态流转 OPEN→IN_PROGRESS→RESOLVED→CLOSED、回复时间线、分配处理人、分页筛选）；知识库（KnowledgeArticle）文章 CRUD/发布/关键字搜索；SLA 策略（SlaPolicy，按优先级配置响应/解决时限）与工单 SLA 到期时间计算、超时标记（正常/即将超时/已超时）及超时统计。前端新增工单列表/详情、知识库列表、SLA 策略配置页。

## Technical Context

**Language/Version**: Java 17 / TypeScript 5（沿用既有技术栈）

**Primary Dependencies**: MyBatis-Plus、Spring Validation、AuditService、Security（角色 ADMIN/SUPPORT/SALES）

**Storage**: MySQL 新增 `ticket`/`ticket_reply`/`knowledge_article`/`sla_policy` 四张表（Flyway V34~V37）

**Testing**: JUnit 5 + Spring Boot Test（TicketServiceTest/KnowledgeArticleServiceTest/SlaPolicyServiceTest 单元、CustomerServiceIT 集成）

**Target Platform**: Web（工单列表/详情页 + 知识库页 + SLA 策略配置页）

**Project Type**: 现有 Web 应用新增模块

**Performance Goals**: 工单创建/列表 ≤1s（SC-C01）

**Constraints**: 优先级枚举 LOW/MEDIUM/HIGH/URGENT；状态单向流转 CLOSED 不可回退；工单必关联客户；SLA 时限按小时、未配置优先级策略则无 SLA；知识库搜索仅返回已发布；SALES 仅看关联自身客户的工单；SLA 策略仅 ADMIN 可配

**Scale/Scope**: 工单 ≤ 数千；知识库文章 ≤ 数百；SLA 策略 ≤ 10 条

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | Controller→Service→持久层 | ✅ 满足（复用既有分层） |
| 原则三：数据完整性、安全与校验 | DTO 校验、服务端授权 | ✅ 满足（Bean Validation + @PreAuthorize 角色控制） |
| 原则四：测试优先与质量门禁 | 测试先于实现、合并门禁 | ✅ 满足（单元/集成测试 + 既有门禁） |
| 原则五：简洁、可维护与可观测 | 无 N+1、审计 | ✅ 满足（批量装配 + AuditService） |

**结论**: 无门禁违规。

## Project Structure

```text
specs/015-customer-service/
├── spec.md / plan.md / tasks.md / research.md / data-model.md / quickstart.md
├── contracts/tickets.md + knowledge.md + sla.md

backend/src/main/java/com/crm/
├── entity/Ticket.java + TicketReply.java + KnowledgeArticle.java + SlaPolicy.java
├── repository/（对应 4 个 Mapper）
├── dto/ticket/（TicketRequest/TicketResponse/TicketReplyRequest/TicketReplyResponse）
├── dto/knowledge/（ArticleRequest/ArticleResponse）
├── dto/sla/（SlaPolicyRequest/SlaPolicyResponse/TicketSlaStatus）
├── service/TicketService.java（CRUD/状态流转/回复/分配/SLA 计算/超时标记）
├── service/KnowledgeArticleService.java（CRUD/发布/搜索）
├── service/SlaPolicyService.java（策略 CRUD/启用）
├── controller/TicketController.java + KnowledgeArticleController.java + SlaPolicyController.java
├── common/ErrorCode.java（新增 TICKET_*/ARTICLE_*/SLA_* 错误码）
└── resources/db/migration/V34__ticket.sql + V35__ticket_reply.sql + V36__knowledge_article.sql + V37__sla_policy.sql

backend/src/test/java/com/crm/
├── service/TicketServiceTest.java + KnowledgeArticleServiceTest.java + SlaPolicyServiceTest.java
├── integration/CustomerServiceIT.java

frontend/src/
├── types/ticket.ts + services/ticketService.ts + types/knowledge.ts + services/knowledgeService.ts
├── types/sla.ts + services/slaService.ts
├── pages/tickets/TicketListPage.tsx + TicketDetailPage.tsx
├── pages/knowledge/KnowledgeArticleListPage.tsx
├── pages/sla/SlaPolicyListPage.tsx
└── App.tsx（客户服务菜单 + 路由）
```

**Structure Decision**: 沿用既有前后端目录结构（Option 2 Web 应用）。工单回复为独立表（一对多时间线）；SLA 到期时间在创建/状态流转时计算并落库，超时标记按需刷新；SALES 行级过滤在 Service 层按 customer.owner_id 校验。

## Complexity Tracking

无违规，本表留空。
