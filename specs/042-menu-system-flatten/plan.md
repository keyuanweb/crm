# Implementation Plan: 系统管理菜单提升与重组模块

**Branch**: `042-menu-system-flatten` | **Date**: 2026-08-24 | **Spec**: [spec.md](./spec.md)

## Summary

将 041 的系统管理二级嵌套扁平化：三个二级子组（组织与权限/流程与配置/审计与维护）提升为一级分组，"组织与权限"更名为"系统管理"。保留 028 权限过滤与移动端递归拍平。

## Technical Context

**Language/Version**: TypeScript 5 / React 18（沿用）

**Primary Dependencies**: antd Menu（单层 submenu 分组）

**Storage**: 无（纯前端）

**Testing**: 前端 typecheck / lint / build；人工验收（一级分组/归属/移动端）

**Target Platform**: Web（桌面单层分组 + 移动 <768px 拍平）

**Project Type**: 既有 Web 应用前端调整

**Constraints**: 不改后端/契约/迁移；保持 028 权限过滤与 040 分组体系；移动端拍平行为不变

**Scale/Scope**: 单文件 `frontend/src/App.tsx`（groupedMenuItems 结构调整 + openKeys 自动展开更新）

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | 表现层纯净 | ✅ 满足（纯导航配置） |
| 原则五：简洁、可维护与可观测 | 结构清晰、无过度设计 | ✅ 满足（扁平化减少层级） |

**结论**: 无门禁违规。

## Project Structure

```text
specs/042-menu-system-flatten/
├── spec.md / plan.md / tasks.md / research.md / quickstart.md
└── checklists/requirements.md

frontend/src/App.tsx  # groupedMenuItems：g-admin 拆为 g-admin(系统管理)/g-config(流程与配置)/g-audit(审计与维护) 三个一级分组；openKeys 逻辑更新
```

**Structure Decision**: 三个一级分组各含 2~5 项；openKeys 自动展开逻辑按新分组 key 更新；递归拍平保持。

## Complexity Tracking

无违规，本表留空。
