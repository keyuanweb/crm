# Research: 移动端 PWA

## R1 manifest 字段

**决策**: `manifest.webmanifest`：
- name: "CRM 客户关系管理系统"，short_name: "CRM"
- display: "standalone"，start_url: "/"，scope: "/"
- theme_color: "#1677ff"，background_color: "#ffffff"
- icons: icon-192.png / icon-512.png（maskable + any）

## R2 Service Worker 策略

**决策**: `sw.js`（VERSION 常量）：
- install：预缓存应用外壳（"/"、"/index.html"、manifest、图标）
- fetch：缓存优先（同源 GET 命中缓存返回），网络回退 + 成功则更新缓存
- activate：清理旧版本缓存（删除非当前 VERSION 的 cache）
- 不缓存 /api/**（业务数据），避免令牌/隐私问题

## R3 安装提示

**决策**: `InstallPrompt` 组件监听 `beforeinstallprompt`，事件触发时显示"安装到主屏幕"提示（可关闭）；`appinstalled` 后隐藏。仅支持时显示。

## R4 移动端快捷入口

**决策**: DashboardPage 顶部，isMobile 时渲染 3 个大按钮（客户/记跟进/线索），按钮高 ≥44px，点击导航对应页。

## R5 SW 注册

**决策**: `main.tsx` 在生产（import.meta.env.PROD）注册 `/sw.js`；开发环境不注册（避免缓存干扰开发）。冒烟用 devtools 或临时启用。
