# 任务清单：ProTable 搜索表单按钮国际化

**功能分支**: `076-protable-i18n`

**创建日期**: 2026-08-30

**状态**: 草稿

## 任务列表

### 任务 1：修改 main.tsx 添加 ProConfigProvider ✅

- **依赖**: 无
- **优先级**: P0
- **描述**: 在 main.tsx 中导入 ProConfigProvider、zhCNIntl、enUSIntl，创建 LocaleProvider 组件，根据当前 i18n 语言动态设置 ProConfigProvider 的 intl prop。
- **验收**: main.tsx 包含 ProConfigProvider 配置，TypeScript 编译通过
- **状态**: 已完成

## 验证任务

### 任务 2：TypeScript 编译验证 ✅

- **依赖**: 任务 1
- **优先级**: P0
- **描述**: 运行 TypeScript 编译检查，确保无错误。
- **验收**: typecheck 通过
- **状态**: 已完成

### 任务 3：浏览器功能验证

- **依赖**: 任务 2
- **优先级**: P0
- **描述**: 在浏览器中验证 ProTable 搜索表单按钮国际化。
- **验收**: 切换语言后，搜索表单按钮正确显示对应语言
- **状态**: 已完成

## 追加任务：菜单 i18n 键显示 bug 修复

### 任务 4：修复 MENU_I18N_KEYS 缺失映射 ✅

- **依赖**: 无
- **优先级**: P0
- **描述**: 在 App.tsx 的 `MENU_I18N_KEYS` 映射表中添加 `'/data-vision': 'dataVision'` 条目，修复 `/data-vision` 菜单项 fallback 到硬编码中文 name 导致 i18n key 找不到翻译的问题。
- **验收**: `/data-vision` 菜单项在中文/英文界面下分别显示"酷炫大屏"/"Data Vision"
- **状态**: 已完成

### 任务 5：修复硬编码中文字符串 ✅

- **依赖**: 任务 4
- **优先级**: P0
- **描述**: 修复 `salesRoutes` 中 `'销售Playbook'` 硬编码中文字符串。
- **验收**: 销售 Playbook 菜单项随语言切换正确显示
- **状态**: 已完成

### 任务 6：TypeScript 编译验证 ✅

- **依赖**: 任务 4, 5
- **优先级**: P0
- **描述**: 运行 TypeScript 编译检查，确保无错误。
- **验收**: `npx tsc --noEmit` 通过
- **状态**: 已完成

### 任务 7：浏览器功能验证

- **依赖**: 任务 6
- **优先级**: P0
- **描述**: 在浏览器中验证菜单 i18n 键显示修复。
- **验收**: 切换语言后，所有菜单项正确显示对应语言，无 raw key 显示
- **状态**: 已完成
