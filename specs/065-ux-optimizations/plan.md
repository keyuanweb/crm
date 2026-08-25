# Implementation Plan: 体验优化批次

**Branch**: `065-ux-optimizations` | **Date**: 2026-08-26 | **Spec**: [spec.md](./spec.md)

## Summary

用户反馈驱动的 5 组体验优化（已实现，本计划记录技术方案与验证）：预测去重、详情页编辑、产品多币种价集成、整体布局、文案/显示修正。

## Technical Context

**Language/Version**: Java 17 / TypeScript 5

**Primary Dependencies**: StageConversionService（019）、currencyService（057）、react-i18next（060）

**Storage**: 无迁移

**Testing**: DashboardStatsServiceTest（聚合）、前端 typecheck/lint/build

**Target Platform**: Web

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | 聚合修正后端、展示兜底前端 | ✅ 满足 |
| 原则四：测试优先与质量门禁 | 既有测试无回归 | ✅ 满足 |
| 原则五：简洁、可维护与可观测 | 前端兜底 + 后端聚合双保险 | ✅ 满足 |

**结论**: 无门禁违规。

## Project Structure

```text
specs/065-ux-optimizations/
├── spec.md / plan.md / tasks.md / research.md / quickstart.md
└── contracts/ux-optimizations.md

frontend/src/
├── pages/stats/DashboardPage.tsx（aggregateForecast 兜底去重）
├── pages/customers/CustomerDetailPage.tsx（编辑入口/头部增强/响应式）
├── pages/products/ProductListPage.tsx（多币种价集成/响应式）
├── App.tsx（布局：限宽/折叠/404/滚动复位/过渡）
├── pages/NotFoundPage.tsx（新增）
├── i18n/（插值修正 {{name}}/{{year}}）
└── index.css（page-container/page-scroll/page-fade/卡片间距）

backend/src/main/java/com/crm/
├── service/DashboardStatsService.java（computeForecast 按阶段聚合）
└── service/CustomerService.java（列表完整显示；toResponse 补 ownerId）
```

## Complexity Tracking

无违规，本表留空。
