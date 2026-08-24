# 快速开始：移动端 PWA

## 前端

1. `public/manifest.webmanifest`：PWA 清单。
2. `public/sw.js`：应用外壳缓存 SW。
3. `public/icon-192.png / icon-512.png / apple-touch-icon.png`：图标（简单生成）。
4. `index.html`：manifest link + iOS 元数据 + theme-color。
5. `main.tsx`：生产注册 SW。
6. `InstallPrompt` 组件：beforeinstallprompt 安装提示。
7. `DashboardPage`：isMobile 快捷入口条。

## 验证

- `pnpm run typecheck` + `lint` + `test`（安装提示/快捷入口组件测试）。
- 冒烟：localhost 打开，devtools → Application → Manifest 校验通过；触发安装提示。
- 桌面端回归：桌面功能不受影响（测试全通过）。

## 备注

- 开发环境不注册 SW（避免缓存干扰）；生产构建后生效。
- 离线仅应用外壳。
