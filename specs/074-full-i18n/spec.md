# 功能规格：全项目中英文国际化补全

**功能分支**: `074-full-i18n`

**创建日期**: 2026-08-30

**状态**: 进行中

**输入**: 用户要求"按sdd规范走"，对全项目中英文国际化进行系统化补全。当前已完成的国际化包括：DashboardPage、AnnouncementCard、BreadcrumbNav（首页链接）。仍有大量组件存在硬编码中文。

## 用户场景与测试（必填）

### 用户故事 1 - 完整中文界面（优先级：P0）

用户切换中文后，所有界面元素均显示中文。

**独立测试**：切换语言为中文，浏览所有页面和组件，确认无英文残留。

**验收场景**:
1. **GIVEN** 用户登录，**When** 切换语言为中文，**Then** 所有按钮、标签、提示、弹窗均显示中文。
2. **GIVEN** 中文界面，**When** 执行任何操作（新增、编辑、删除、提交），**Then** 所有消息提示均显示中文。

---

### 用户故事 2 - 完整英文界面（优先级：P0）

用户切换英文后，所有界面元素均显示英文。

**独立测试**：切换语言为英文，浏览所有页面和组件，确认无中文残留。

**验收场景**:
1. **GIVEN** 用户登录，**When** 切换语言为英文，**Then** 所有按钮、标签、提示、弹窗均显示英文。
2. **GIVEN** 英文界面，**When** 执行任何操作，**Then** 所有消息提示均显示英文。

---

### 用户故事 3 - 动态内容处理（优先级：P1）

运行时动态内容（如用户姓名、字段名）保持原样，不尝试国际化。

**独立测试**：动态数据显示正常，不影响国际化效果。

**验收场景**:
1. **GIVEN** 用户姓名、字段名等动态数据，**When** 切换语言，**Then** 动态数据保持原样不变。
2. **GIVEN** 国际化文本，**When** 切换语言，**Then** 静态文本正确切换。

## 需求（必填）

### 功能需求

- **FR-I18N-01**: 所有用户可见的静态文本必须使用 `t()` 函数调用。
- **FR-I18N-02**: 动态数据（用户姓名、字段名、业务数据）保持原样，不国际化。
- **FR-I18N-03**: 代码注释中的中文不需要国际化。
- **FR-I18N-04**: 测试文件中的中文不需要国际化。
- **FR-I18N-05**: 路由路径（如 `/leads`）不需要国际化。
- **FR-I18N-06**: 中英文翻译键必须一一对应，无遗漏。
- **FR-I18N-07**: TypeScript 编译必须通过。
- **FR-I18N-08**: 前端构建必须通过。

### 待国际化组件清单

| 组件 | 文件路径 | 硬编码文本数量 | 优先级 |
|---|---|---|---|
| ContactsCard | `frontend/src/components/ContactsCard.tsx` | ~25 | P0 |
| CommentSection | `frontend/src/components/CommentSection.tsx` | ~15 | P0 |
| FollowUpTimeline | `frontend/src/components/FollowUpTimeline.tsx` | ~18 | P0 |
| NotificationCenter | `frontend/src/components/NotificationCenter.tsx` | ~8 | P0 |
| SurveyBlock | `frontend/src/components/SurveyBlock.tsx` | ~15 | P1 |
| SignSection | `frontend/src/components/SignSection.tsx` | ~15 | P1 |
| SignaturePad | `frontend/src/components/SignaturePad.tsx` | ~5 | P1 |
| InstallPrompt | `frontend/src/components/InstallPrompt.tsx` | ~5 | P1 |
| LeadConvertModal | `frontend/src/components/LeadConvertModal.tsx` | ~12 | P1 |
| ErrorBoundary | `frontend/src/components/ErrorBoundary.tsx` | ~5 | P1 |
| BreadcrumbNav | `frontend/src/components/BreadcrumbNav.tsx` | ~15（分组名称） | P1 |
| CustomFieldItems | `frontend/src/components/CustomFieldItems.tsx` | ~1（动态字段名除外） | P2 |

### 关键实体（涉及数据）

- 无新增实体。涉及文件：
  - `frontend/src/i18n/zh-CN.ts`（中文翻译，需扩展 ~150 个新键）
  - `frontend/src/i18n/en.ts`（英文翻译，需扩展 ~150 个新键）
  - 上述 12 个组件文件

## 成功标准（必填）

### 可度量结果

- **SC-I18N-01**: 所有用户可见文本均使用 `t()` 调用，无硬编码中文/英文残留。
- **SC-I18N-02**: 中英文切换后，界面正确显示对应语言，无乱码或空白。
- **SC-I18N-03**: TypeScript 编译通过（`npx tsc --noEmit` 无错误）。
- **SC-I18N-04**: 前端构建通过（`pnpm build` 无错误）。
- **SC-I18N-05**: 中英文翻译键一一对应，无遗漏。
- **SC-I18N-06**: 动态数据（用户姓名、字段名等）保持原样，不影响国际化。

## 假设

- 所有组件已正确配置 `react-i18next` 环境。
- `useTranslation` hook 可在任何组件中直接使用。
- 动态数据（如用户姓名、字段名）无法国际化，保持原样。

## 依赖

- 060 i18n 国际化基础架构（已完成）
- 066 i18n 页面文案（已完成部分）
- 073 首页重设计（已完成 DashboardPage、AnnouncementCard、BreadcrumbNav 国际化）

## 变更记录

| 日期 | 变更内容 |
|---|---|
| 2026-08-30 | 初始规格创建 |
