# 数据模型：PWA 静态资源

## manifest.webmanifest（静态）

| 字段 | 值 |
|---|---|
| name / short_name | CRM 客户关系管理系统 / CRM |
| display | standalone |
| start_url / scope | / / |
| theme_color / background_color | #1677ff / #ffffff |
| icons | 192 / 512 PNG（maskable + any） |

## sw.js 缓存

| Cache 名 | 内容 |
|---|---|
| crm-shell-v{N} | "/"、"/index.html"、manifest、icon-192/512、apple-touch-icon |

- 不缓存 `/api/**`（业务数据与令牌安全）。

## 约束

- 纯静态资源，无后端表。
- SW 版本号递增触发更新。
