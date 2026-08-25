# Implementation Plan: 托管落地页 + UTM 跟踪模块

**Branch**: `053-landing-page` | **Date**: 2026-08-25 | **Spec**: [spec.md](./spec.md)

## Summary

新增落地页实体与公开访问（/lp/{id}）；UTM 参数在表单提交时捕获存入 FormSubmission 扩展列；提供 UTM 归因统计。

## Technical Context

**Language/Version**: Java 17 / TypeScript 5（沿用）

**Primary Dependencies**: FormService（036）、FormSubmission、SecurityConfig

**Storage**: MySQL 新增 `landing_page` 表 + `form_submission` 扩展 UTM 列（V62）

**Testing**: JUnit 5（LandingPageServiceTest 单元、LandingPageIT 集成）

**Target Platform**: Web（落地页配置页 + 公开 /lp/{id} 页面 + UTM 统计）

**Project Type**: 既有模块增强（036）+ 新增

**Performance Goals**: 公开渲染 ≤200ms

**Constraints**: 落地页配置仅 ADMIN；公开访问白名单；关联表单须 ENABLED

**Scale/Scope**: 落地页 ≤ 数十

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | 独立 LandingPageService/Controller | ✅ 满足 |
| 原则三：数据完整性、安全与校验 | 白名单、DTO 校验 | ✅ 满足 |
| 原则四：测试优先与质量门禁 | 测试先于实现 | ✅ 满足 |
| 原则五：简洁、可维护与可观测 | 复用 036 提交链路、UTM 列扩展 | ✅ 满足 |

**结论**: 无门禁违规。

## Project Structure

```text
specs/053-landing-page/
├── spec.md / plan.md / tasks.md / research.md / data-model.md / quickstart.md
├── contracts/landing-page.md

backend/src/main/java/com/crm/
├── entity/LandingPage.java + repository/LandingPageMapper.java
├── entity/FormSubmission.java（+utm 列）
├── dto/landing/（LandingPageRequest/Response/LandingPagePublicResponse/UtmStatsResponse）
├── service/LandingPageService.java（CRUD + 公开渲染 + UTM 统计）
├── controller/LandingPageController.java（/landing-pages 管理 + /public/lp 渲染）
├── common/ErrorCode.java（新增 LANDING_* 错误码）
└── resources/db/migration/V62__landing_page.sql

backend/src/test/java/com/crm/
├── service/LandingPageServiceTest.java
├── integration/LandingPageIT.java

frontend/src/
├── services/landingPageService.ts + types/landingPage.ts
├── pages/landing/LandingPageListPage.tsx（配置 CRUD）
├── pages/landing/LandingPageView.tsx（公开 /lp/:id 渲染）
└── App.tsx（营销中心组点亮"落地页"占位项 → 管理页路由）
```

**Structure Decision**: LandingPageService 独立；UTM 解析在 FormService 提交链中（复用 036 的公开提交端点，扩展捕获 UTM）；公开渲染走 /api/v1/public/lp。

## Complexity Tracking

无违规，本表留空。
