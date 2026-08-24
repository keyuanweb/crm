# Implementation Plan: 移动端 PWA

**Branch**: `027-mobile-pwa` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

## Summary

为前端启用 PWA：`public/manifest.webmanifest`（独立显示/图标/主题色）+ `public/sw.js`（应用外壳缓存，缓存优先+网络回退+版本更新）+ iOS 元数据（index.html）+ `main.tsx` 注册 SW + `beforeinstallprompt` 安装提示组件 + DashboardPage 移动端快捷入口条（isMobile 渲染客户/跟进/线索大按钮）。

## Technical Context

**Language/Version**: TypeScript/React 18、Vite 5（前端；本功能纯前端无后端改动）

**Primary Dependencies**: Vite（public/ 静态资源）、React 18、antd 5（Button/Card/Grid）；原生 Service Worker API

**Storage**: SW Cache API（应用外壳缓存）；无新表

**Testing**: Vitest + RTL（安装提示组件、快捷入口渲染测试）；SW 策略用静态代码审查 + 冒烟（devtools 验证）

**Target Platform**: 移动端浏览器（Chrome/Safari，PWA 需 HTTPS 或 localhost）

**Project Type**: Web 应用（前端增强）

**Performance Goals**: 应用外壳加载 < 2s（缓存命中）；首屏离线可用

**Constraints**: 原生 SW（不引 workbox）；离线仅应用外壳；复用现有 isMobile 响应式

**Scale/Scope**: manifest + sw + index.html 元数据 + main.tsx 注册 + 安装提示组件 + 快捷入口条

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则一：契约优先的 API 设计 | 端点契约先于实现 | ✅ 满足（无新后端端点；前端静态契约） |
| 原则二：分层架构与关注点分离 | 前端表现层纯净 | ✅ 满足（PWA 为纯表现层增强，不引入业务规则） |
| 原则三：数据完整性、安全与校验 | 安全边界 | ✅ 满足（SW 仅缓存静态资源，不缓存业务数据/令牌） |
| 原则四：测试优先与质量门禁 | 测试先于实现 | ✅ 满足（安装提示/快捷入口组件测试；SW 冒烟） |
| 原则五：简洁、可维护与可观测 | YAGNI | ✅ 满足（原生 SW，不引 workbox） |

**结论**: 无门禁违规。

## Project Structure

### Documentation (this feature)

```text
specs/027-mobile-pwa/
├── plan.md / spec.md / research.md / data-model.md / quickstart.md
├── contracts/（pwa 静态契约）
└── tasks.md
```

### Source Code (repository root)

```text
frontend/
├── public/
│   ├── manifest.webmanifest                 # 新增：PWA 清单
│   ├── sw.js                                # 新增：Service Worker（应用外壳缓存）
│   ├── icon-192.png / icon-512.png          # 新增：应用图标（简单生成）
│   └── apple-touch-icon.png                 # 新增：iOS 图标
├── index.html                               # 修改：manifest link + iOS 元数据 + theme-color
├── src/main.tsx                             # 修改：注册 SW（生产环境）
├── src/components/InstallPrompt.tsx         # 新增：beforeinstallprompt 安装提示
├── src/pages/stats/DashboardPage.tsx        # 修改：isMobile 快捷入口条（客户/跟进/线索）
└── src/test/setup.ts                        # 修改：mock navigator.serviceWorker 等
```

**Structure Decision**: 纯前端增强。manifest/sw/图标放 public/（Vite 构建时静态拷贝）；SW 用原生 Cache API 缓存应用外壳（缓存优先、网络回退、版本号控制）；安装提示监听 beforeinstallprompt；快捷入口复用 DashboardPage（isMobile 时渲染）。

## Complexity Tracking

> 无违规，本表留空。
