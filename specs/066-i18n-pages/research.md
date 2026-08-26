# Research: 更多页面国际化模块

**Branch**: `066-i18n-pages` | **Date**: 2026-08-26

## 1. 资源组织策略

**Decision**: 每个页面一个 JSON 文件（如 `resources/customer.json`），key 按 `page.section.field` 命名（如 `customer.list.title`、`common.button.edit`）。zh-CN/en.ts 注册新命名空间。

**Rationale**: 每页独立文件便于维护；公共文案提取到 common.json 避免重复。

**Alternatives considered**: 单文件所有 key——过大难维护；React i18next namespace 加载按需——当前全部预加载，简单可靠。

## 2. 命名规范

**Decision**: 
- 按钮：`common.button.{action}`（edit/save/cancel/delete/share）
- 状态：`common.status.{value}`（active/inactive/pending/approved）
- 列标题：`{page}.column.{field}`（customer.name/product.price）
- 表单标签：`{page}.form.{field}`（customer.form.phone）
- 消息提示：`common.message.{type}`（success/error/validation）
- 占位符：`{page}.placeholder.{field}`

**Rationale**: 统一前缀便于搜索替换和审查。

## 3. 迁移策略

**Decision**: 先做客户管理页面（最高使用频率），再逐步覆盖其他页面。每次提交一个页面，确保 typecheck/lint/build 通过。

**Rationale**: 增量交付，每步可验证；避免大改导致回归。
