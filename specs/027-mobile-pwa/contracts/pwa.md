# 契约：PWA 静态配置

## Web App Manifest（/manifest.webmanifest）

标准 PWA 清单，供浏览器"添加到主屏幕"。字段见 data-model.md。

## Service Worker（/sw.js）

- 缓存应用外壳（"/"、index.html、manifest、图标），缓存优先 + 网络回退。
- 不缓存 `/api/**`。
- activate 时清理旧版本缓存。

## index.html 元数据

- `<link rel="manifest" href="/manifest.webmanifest">`
- `<meta name="theme-color" content="#1677ff">`
- iOS：`apple-mobile-web-app-capable`、`apple-mobile-web-app-status-bar-style`、`apple-touch-icon`

## 备注

- 无后端 API 变更。
- PWA 仅 HTTPS 或 localhost 生效。
