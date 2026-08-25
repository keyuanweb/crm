# Research: 国际化（i18n）模块

**Branch**: `060-i18n` | **Date**: 2026-08-25

## 1. 方案选择

**Decision**: i18next + react-i18next（前端社区标准）。初始化时读 localStorage `app_lang`（默认 zh-CN），`fallbackLng: 'zh-CN'` 保证未翻译 key 回退中文。

**Rationale**: i18next 生态成熟、React 集成简单；fallback 保证中文兜底。

**Alternatives considered**: 自研切换（context）——无生态；vue-i18n——技术栈不符。i18next 最合适。

## 2. 资源组织

**Decision**: `zh-CN.ts` / `en.ts` 命名空间结构：`menu.*`（菜单）、`app.*`（系统名/页脚）、`login.*`（登录页）、`home.*`（首页问候）。菜单分组/项 key 与 App.tsx 路由定义对齐。

**Rationale**: 命名空间清晰；菜单 key 集中便于维护。

## 3. 范围收敛

**Decision**: v1 覆盖核心框架（系统名/菜单/顶栏/页脚/登录/首页问候）；业务页面文案渐进迁移（后续模块逐个接入）。

**Rationale**: 全站迁移工作量大；核心框架资源化建立 i18n 基础，业务页渐进接入。
