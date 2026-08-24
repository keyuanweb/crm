# Implementation Plan: 系统管理子菜单细分模块

**Branch**: `041-menu-system-split` | **Date**: 2026-08-24 | **Spec**: [spec.md](./spec.md)

## Summary

将"系统管理"组内 11 项细分为 3 个二级子组：组织与权限（用户/角色/部门）、流程与配置（工作流/审批流配置/SLA 策略/合同模板/自定义字段）、审计与维护（标签与细分/审计日志/回收站）。antd Menu 二级嵌套；移动端递归拍平。

## Technical Context

**Language/Version**: TypeScript 5 / React 18（沿用）

**Primary Dependencies**: antd Menu（submenu 多级嵌套）

**Storage**: 无（纯前端）

**Testing**: 前端 typecheck / lint / build；人工验收（二级子组/归属/移动端）

**Target Platform**: Web（桌面嵌套 + 移动 <768px 拍平）

**Project Type**: 既有 Web 应用前端重构

**Constraints**: 不改后端/契约/迁移；保持 028 权限过滤与 040 分组体系；移动端拍平行为不变

**Scale/Scope**: 单文件 `frontend/src/App.tsx`（adminRoutes 分组 + groupedMenuItems 嵌套 + 拍平递归）

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | 表现层纯净 | ✅ 满足（纯导航配置） |
| 原则五：简洁、可维护与可观测 | 结构清晰、无过度设计 | ✅ 满足（仅系统管理组二级嵌套，其余不变） |

**结论**: 无门禁违规。

## Project Structure

```text
specs/041-menu-system-split/
├── spec.md / plan.md / tasks.md / research.md / quickstart.md
└── checklists/requirements.md

frontend/src/App.tsx  # adminRoutes 分组数组 + groupedMenuItems 嵌套 + 移动端递归拍平
```

**Structure Decision**: 系统管理 11 项拆为 3 个子组数组（adminOrgRoutes/adminConfigRoutes/adminAuditRoutes），groupedMenuItems 中 g-admin 的 children 为二级 submenu；移动端用递归拍平函数代替 flatMap。

## Complexity Tracking

无违规，本表留空。
