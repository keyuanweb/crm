# Research: 系统管理菜单提升与重组模块

**Branch**: `042-menu-system-flatten` | **Date**: 2026-08-24

## 1. 扁平化决策

**Decision**: 041 的系统管理二级嵌套（g-admin → 3 子组）提升为 3 个一级分组：
- **系统管理**（原"组织与权限"更名）：用户管理、角色权限、部门
- **流程与配置**：工作流、审批流配置、SLA 策略、合同模板、自定义字段
- **审计与维护**：标签与细分、审计日志、回收站

**Rationale**: 用户明确要求二级菜单全部提到一级（减少点击层级），且"组织与权限"命名不够直观，更名"系统管理"更贴合用户心智（系统管理=账号/权限/组织）。

**Alternatives considered**: 保持二级嵌套（用户已拒绝）；合并为单一系统管理组（11 项过长，已属 041 解决的痛点）。扁平化为三个一级分组最符合需求。

## 2. openKeys 自动展开

**Decision**: openKeys 受控展开逻辑按新分组 key 更新：进入 /users|/roles|/departments 展开 g-admin；/workflows|/approval-flows|/settings/custom-fields|/contract-templates|/sla-policies 展开 g-config；/tags|/audit-logs|/recycle-bin 展开 g-audit。移动端递归拍平不变。

**Rationale**: 一级分组仍需要自动展开对应分组，保证导航直达；递归拍平兼容扁平结构。
