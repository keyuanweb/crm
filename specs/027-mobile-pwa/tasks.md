# Tasks: 移动端 PWA



**Input**: Design documents from `/specs/027-mobile-pwa/`



**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/pwa.md



**Tests**: 安装提示组件测试、快捷入口渲染测试、SW 策略代码审查 + devtools 冒烟验证

## Phase 1: PWA 基础设施搭建 (S1 可安装 PWA)



- [x] T001 [P] [US1] 创建并编写 `frontend/public/manifest.webmanifest` 配置 name/short_name/display=standalone/start_url/theme_color=#1677ff/icons(192/512) 图标
- [x] T002 [P] [US1] 创建 `frontend/public/icon-192.png`、`icon-512.png` 图标和 `apple-touch-icon.png` 图标，生成简单 SVG/PNG 应用图标
- [x] T003 [P] [US1] 修改 `index.html` 添加 manifest link + theme-color + iOS 元数据（apple-mobile-web-app-capable、status-bar-style、apple-touch-icon）

## Phase 2: Service Worker 实现 (S2 离线可用)

- [x] T004 [P] [US2] 创建并编写 `frontend/public/sw.js` 配置 VERSION 预缓存 install 事件检测请求 fetch 事件缓存优先+网络回退策略（/"index.html"、"manifest"、静态资源）；/api/** 网络优先；activate 事件清理旧缓存
- [x] T005 [US2] 修改 `main.tsx` 生产环境注册 SW（`import.meta.env.PROD` 判断）；`/sw.js` 注册；`src/test/setup.ts` mock navigator.serviceWorker

## Phase 3: 安装提示与外勤快捷入口 (US1/US3 快捷入口)

- [x] T006 [P] [US1] 创建并编写 `src/components/InstallPrompt.tsx` 组件监听 beforeinstallprompt 事件显示"添加到主屏幕"提示按钮；点击触发安装；监听 appinstalled 事件重置状态
- [x] T007 [US3] 修改 `DashboardPage.tsx` isMobile 时渲染快捷入口条（客户/跟进/线索大按钮，触控目标 ≥44px）；复用现有 DashboardPage，isMobile 条件渲染

## Phase 4: 质量检查与冒烟测试

- [x] T008 运行 `pnpm run typecheck` + `lint` + `test` 确保类型检查、代码规范、单元测试通过
- [x] T009 [P] 本地 localhost 冒烟测试（devtools Application 面板 Manifest 校验、SW 注册验证、安装提示触发验证；断网刷新验证离线可用；快捷入口点击跳转验证）

## Dependencies & Execution Order



- T001/T002/T003 完成后即可验证 US1 可安装 PWA
- T004 完成后执行 T005 注册 SW
- T006 完成后执行 T007 快捷入口集成
- Phase 4 在所有任务完成后执行质量检查

## Notes



- PWA 功能仅限 HTTPS/localhost 环境生效
- SW 仅缓存静态资源，不缓存业务数据/令牌（YAGNI，不引入 workbox）
- 图标使用简单 SVG/PNG 生成（基于现有 TeamOutlined 或系统首字母）
- PWA 仅在 HTTPS/localhost 生效
