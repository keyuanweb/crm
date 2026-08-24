# Research: 系统管理子菜单细分模块

**Branch**: `041-menu-system-split` | **Date**: 2026-08-24

## 1. 二级子组划分

**Decision**: 系统管理 11 项按功能域分 3 子组：
- **组织与权限**：用户管理、角色权限、部门（账号与组织架构）
- **流程与配置**：工作流、审批流配置、SLA 策略、合同模板、自定义字段（业务规则与元数据配置）
- **审计与维护**：标签与细分、审计日志、回收站（数据治理与系统运维）

**Rationale**: 语义聚合——账号/权限归组织域；工作流/审批/SLA/模板/字段都是"配置规则"；标签/审计/回收站偏数据维护。每子组 2~5 项，展开后目标定位快。

**Alternatives considered**: 平铺保持（用户已反馈太长，否）；拆成两个顶层组（增菜单层级负担，且系统管理语义应聚合）。二级嵌套最合适。

## 2. 技术实现

**Decision**: antd Menu `submenu` 支持多级嵌套——`g-admin` 的 children 为 3 个二级 submenu（每个含 items）。移动端拍平由 `flatMap(g => g.children)` 改为递归拍平函数（flattenMenuItems），兼容二级 children 结构。

**Rationale**: antd 原生支持嵌套 submenu，无新依赖；递归拍平保证移动端全部项可达。

**Alternatives considered**: 手写展开/折叠状态——antd 已内置，无需自研。
