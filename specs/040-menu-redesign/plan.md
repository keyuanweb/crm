# Implementation Plan: 菜单分类重设计模块

**Branch**: `040-menu-redesign` | **Date**: 2026-08-24 | **Spec**: [spec.md](./spec.md)

## Summary

重设计左侧菜单分组：8 个业务分组 + 首页置顶。移除过薄的"基础资料"组（任务→工作台、产品→销售管理）；公告管理与我的审批从系统管理移至服务协作（全员功能）；流失预警归客户管理、数据大屏/智能建议归工作台；数据分析仅留报表/排行/导出。保持 028 角色过滤与移动端拍平。

## Technical Context

**Language/Version**: TypeScript 5 / React 18（沿用）

**Primary Dependencies**: antd Menu（submenu 分组）、React Router

**Storage**: 无（纯前端）

**Testing**: 前端 typecheck / lint / build；人工验收（分组核对/角色可见性/路由可达）

**Target Platform**: Web（桌面分组 + 移动 <768px 拍平）

**Project Type**: 既有 Web 应用前端重构

**Constraints**: 不改后端/契约/迁移；保持 028 权限体系（menuKeyOf 映射随移动同步）；移动端拍平行为不变

**Scale/Scope**: 单文件 `frontend/src/App.tsx` 路由分组重排

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | 表现层纯净 | ✅ 满足（纯导航配置，无业务逻辑） |
| 原则五：简洁、可维护与可观测 | 命名表达意图、结构清晰 | ✅ 满足（分组数组化，消除过载分组） |

**结论**: 无门禁违规。

## Project Structure

```text
specs/040-menu-redesign/
├── spec.md / plan.md / tasks.md / research.md / quickstart.md
└── checklists/requirements.md

frontend/src/App.tsx  # 路由分组数组重排 + groupedMenuItems 标签 + menuKeyOf 同步
```

**Structure Decision**: 全部改动在 App.tsx 一个文件；分组定义为命名数组（customerRoutes/salesRoutes/dealRoutes/marketingRoutes/serviceRoutes/workbenchRoutes/dataRoutes/adminRoutes）。

## Complexity Tracking

无违规，本表留空。
