# Implementation Plan: 国际化（i18n）模块

**Branch**: `060-i18n` | **Date**: 2026-08-25 | **Spec**: [spec.md](./spec.md)

## Summary

前端接入 i18n：`frontend/src/i18n/`（i18next 初始化 + zh/en 资源）。核心文案（系统名/菜单分组与项/登录页/顶栏/首页）改用 `useTranslation`。语言偏好 localStorage 持久化。未翻译 key 回退中文。

## Technical Context

**Language/Version**: TypeScript 5 / React 18（沿用）

**Primary Dependencies**: i18next + react-i18next（前端新增依赖）

**Storage**: localStorage（语言偏好）

**Testing**: 前端 typecheck / lint / build + 手动验收

**Target Platform**: Web

**Project Type**: 前端框架增强

**Performance Goals**: 切换即时生效（无延迟）

**Constraints**: 默认 zh-CN；未翻译回退；v1 核心界面

**Scale/Scope**: 核心文案资源（系统名/菜单/登录/首页）

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | i18n 独立模块 | ✅ 满足 |
| 原则五：简洁、可维护与可观测 | key 集中管理 | ✅ 满足 |

**结论**: 无门禁违规。

## Project Structure

```text
specs/060-i18n/
├── spec.md / plan.md / tasks.md / research.md / quickstart.md
└── checklists/requirements.md

frontend/src/
├── i18n/index.ts（初始化：i18next + zh/en 资源 + localStorage 持久化 + fallback zh）
├── i18n/zh-CN.ts + i18n/en.ts（资源）
├── App.tsx（系统名/菜单分组与项/顶栏/页脚 useTranslation）
├── pages/LoginPage.tsx（登录页文案）
└── pages/stats/DashboardPage.tsx（首页问候）
```

**Structure Decision**: 轻量 i18n（i18next）；菜单文案 key 化（menu.common/customer/sales 等命名空间）；核心页面逐步接入。

## Complexity Tracking

无违规，本表留空。
