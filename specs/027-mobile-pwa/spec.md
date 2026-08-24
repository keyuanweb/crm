# Feature Specification: 移动端 PWA

**Feature Branch**: `027-mobile-pwa`

**Created**: 2026-08-23

**Status**: Draft

**Input**: User description: "移动端 PWA：manifest+service worker 使应用可安装、离线可用，外勤场景优化（首页快捷入口、触屏适配），聚焦看客户/记跟进高频动作"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 可安装 PWA (Priority: P1)

用户可通过浏览器"添加到主屏幕"将系统安装为独立应用（有图标、独立窗口、全屏运行），像原生 App 一样使用。

**Why this priority**: 外勤销售在手机上访问系统，可安装的 PWA 提供接近原生的体验（独立图标、全屏、无浏览器地址栏），提升使用频率。

**Independent Test**: 可独立验证——浏览器地址栏出现安装按钮（或通过 devtools 触发 beforeinstallprompt），安装后主屏幕出现应用图标。

**Acceptance Scenarios**:

1. **Given** 用户使用移动端浏览器访问系统，**When** 触发"添加到主屏幕"，**Then** 应用以独立窗口安装，含图标与名称。
2. **Given** 已安装应用，**When** 打开，**Then** 以全屏模式运行（无浏览器地址栏），主题色正确。

### User Story 2 - 离线可用与基础缓存 (Priority: P2)

Service Worker 缓存应用外壳（HTML/JS/CSS 与静态资源），离线时可打开应用并看到上次缓存的界面；网络恢复自动同步。

**Why this priority**: 外勤场景网络不稳定，离线可查看已加载页面（如客户列表缓存）是实用保障。

**Independent Test**: 可独立验证——首次在线访问后断网，刷新页面仍能打开应用外壳（显示缓存内容而非白屏）。

**Acceptance Scenarios**:

1. **Given** 用户首次在线访问系统，**When** 断网后重新打开，**Then** 应用外壳（框架/菜单）可加载，非白屏。
2. **Given** 网络恢复，**When** 刷新，**Then** 自动加载最新内容（SW 更新策略）。

### User Story 3 - 外勤快捷入口（可选） (Priority: P2)

移动端首页（PWA 独立入口）提供高频动作快捷入口：查看客户、记跟进、查看线索，触屏友好的大按钮布局。

**Why this priority**: 外勤核心动作（看客户、记跟进）一键直达，减少导航层级。

**Independent Test**: 可独立验证——移动端首页显示快捷入口卡，点击直达客户列表/跟进表单。

**Acceptance Scenarios**:

1. **Given** 移动端打开系统首页，**When** 查看，**Then** 显示"客户/跟进/线索"快捷入口卡（大触控目标）。
2. **Given** 点击"记跟进"快捷入口，**When** 操作，**Then** 打开跟进创建（选择客户后填写）。

### Edge Cases

- Service Worker 缓存过期/损坏：SW 更新失败时回退网络，不阻塞使用。
- 离线时 API 请求失败：显示友好提示（"网络不可用"），不白屏。
- 多浏览器（Chrome/Safari/Firefox）manifest 兼容：标准 manifest 字段即可，Safari 需 apple-touch-icon。
- PWA 仅限 HTTPS 或 localhost（开发环境 localhost 可验证）。
- 安装提示仅在满足 PWA 条件时出现（不打扰普通访问）。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统必须提供 Web App Manifest（name/short_name/start_url/display=standalone/theme_color/icons），支持"添加到主屏幕"安装。
- **FR-002**: 系统必须提供 iOS 兼容元数据（apple-touch-icon、apple-mobile-web-app-capable、status bar 样式）。
- **FR-003**: 系统必须注册 Service Worker，预缓存应用外壳（HTML/JS/CSS/图标）与静态资源。
- **FR-004**: 系统必须在离线时提供应用外壳（缓存命中），API 请求失败显示友好提示而非白屏。
- **FR-005**: 系统必须在前端处理 `beforeinstallprompt` 事件，满足条件时显示安装提示（可关闭）。
- **FR-006**: 移动端首页必须提供外勤快捷入口（客户/跟进/线索），大触控目标（≥44px），触屏友好。
- **FR-007**: Service Worker 必须采用"缓存优先 + 网络回退"策略并支持版本更新（新版本安装后重新加载生效）。
- **FR-008**: 应用必须适配移动端视口（viewport meta 已有）、触屏交互与安全区（iPhone 刘海屏 env(safe-area-inset)）。

### Key Entities

- **manifest.webmanifest**: PWA 清单（应用标识/图标/主题色/独立显示）。
- **service-worker.js**: 缓存策略与版本管理（前端静态资源，public 目录）。
- **移动端首页（MobileHome）**: 响应式快捷入口组件（复用现有 DashboardPage 或独立精简版）。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 安装成功率——移动端浏览器满足 PWA 条件可安装（manifest 校验通过，Lighthouse 可安装性指标通过）。
- **SC-002**: 离线可用——首次在线加载后断网，应用外壳 100% 可加载（抽查 5 次）。
- **SC-003**: 快捷入口可用——移动端首页 3 个快捷入口点击均跳转正确目标（抽查 5 次）。
- **SC-004**: 触控目标——快捷入口按钮最小高度 ≥44px（设计规范，抽查 5 处）。
- **SC-005**: 无回归——PWA 改动不破坏桌面端功能（桌面端测试全通过）。

## Assumptions

- 前端为 Vite 构建；manifest 与 service worker 放 public/（构建时静态拷贝）。
- Service Worker 用原生 API（不引入 workbox，YAGNI；手写基础缓存策略）。
- 图标用简单 SVG/PNG 生成（基于现有 TeamOutlined 或系统首字母），不做复杂设计。
- 离线仅保证应用外壳（静态资源），业务数据仍需网络（API 不缓存）。
- 移动端快捷入口复用现有页面（不新建独立 MobileHome 页，改为在 DashboardPage 顶部按 isMobile 渲染快捷入口条）。
- PWA 仅 HTTPS/localhost 生效（开发环境 localhost 验证）。
