# Implementation Plan: 前端体验优化模块

**Branch**: `039-dashboard-ux` | **Date**: 2026-08-24 | **Spec**: [spec.md](./spec.md)

## Summary

两项纯前端体验优化：1）菜单切换防闪烁——登录后立即预加载全部懒加载页面 chunk + 菜单导航用 React `startTransition` 包裹（Suspense 挂起保持旧 UI）+ Suspense fallback 改为同高骨架；2）首页"客户分析"卡片布局修复——取消 flex 拉伸、统计项改响应式栅格、公告卡弹性填充。

## Technical Context

**Language/Version**: TypeScript 5 / React 18（沿用既有技术栈）

**Primary Dependencies**: React Router 6（lazy + Suspense）、antd 5（Card/Row/Col/Statistic）、Vite（chunk 分包）

**Storage**: 无（纯前端）

**Testing**: 前端 typecheck / lint / build；人工验收（切换 10 次 0 闪烁）

**Target Platform**: Web（桌面 + 移动 <768px）

**Project Type**: 既有 Web 应用前端优化

**Performance Goals**: 登录后 ≤5 秒完成全部 chunk 预加载；切换目标页 0 等待（SC-UX01）

**Constraints**: 不引入新依赖；不改后端/契约/迁移；预加载失败需静默兜底

**Scale/Scope**: 约 51 个懒加载 chunk；3 个前端文件

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | 表现层纯净、业务在服务端 | ✅ 满足（纯 UI/路由优化，无业务逻辑） |
| 原则五：简洁、可维护与可观测 | YAGNI、不引入重型依赖 | ✅ 满足（零新依赖，复用 React/Router 既有能力） |

**结论**: 无门禁违规。

## Project Structure

```text
specs/039-dashboard-ux/
├── spec.md / plan.md / tasks.md / research.md / quickstart.md
└── checklists/requirements.md

frontend/src/
├── App.tsx                    # 预加载 PRELOAD_PAGES + startTransition 导航 + PageSkeleton fallback
├── pages/stats/DashboardPage.tsx  # 客户分析卡：自然高度 + 响应式栅格
└── components/AnnouncementCard.tsx # flex 弹性填充
```

**Structure Decision**: 全部改动集中在 3 个既有前端文件，无新增目录。

## Complexity Tracking

无违规，本表留空。
