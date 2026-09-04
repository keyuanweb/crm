# 实施计划：ProTable 搜索表单按钮国际化

**功能分支**: `076-protable-i18n`

**创建日期**: 2026-08-30

**状态**: 草稿

## 技术方案

### 问题分析

ProTable 搜索表单中的"重置"和"查询"按钮由 `@ant-design/pro-components` 组件库控制，需要配置 `ProConfigProvider` 的 `intl` prop 来切换语言。

### 实施方案

1. 在 `main.tsx` 中导入 `ProConfigProvider`、`zhCNIntl`、`enUSIntl`。
2. 创建 `LocaleProvider` 组件，根据当前 i18n 语言动态设置 `ProConfigProvider` 的 `intl` prop。
3. 替换原有的 `ConfigProvider` 为 `LocaleProvider`。

### 文件变更

| 文件 | 变更类型 | 说明 |
|---|---|---|
| `frontend/src/main.tsx` | 修改 | 添加 ProConfigProvider 配置 |

### 风险评估

- **风险等级**: 低
- **影响范围**: 全局，但仅影响 ProTable 搜索表单按钮
- **回滚方案**: 恢复 `main.tsx` 原始内容

## 追加修复：菜单 i18n 键显示 bug

### 问题描述

App.tsx 中 `MENU_I18N_KEYS` 映射表缺少 `/data-vision` 条目，导致该菜单项 fallback 到硬编码中文 name 作为 i18n key，找不到翻译时显示 raw key。

### 修复方案

1. 在 `MENU_I18N_KEYS` 中添加 `'/data-vision': 'dataVision'` 映射
2. 恢复 `workbenchRoutes` 中 `/data-vision` 的 `name` 为硬编码中文 `'酷炫大屏'`（与其余菜单项保持一致模式）
3. 修复 `salesRoutes` 中 `'销售Playbook'` 硬编码 → `t('menu.playbook')`

### 文件变更

| 文件 | 变更类型 | 说明 |
|---|---|---|
| `frontend/src/App.tsx` | 修改 | 添加 `/data-vision` 映射 + 修复硬编码中文 |

## 验证计划

1. TypeScript 编译通过（`npx tsc --noEmit`）。
2. 前端构建通过（`pnpm build`）。
3. 浏览器验证：切换语言后，ProTable 搜索表单按钮正确显示对应语言。
