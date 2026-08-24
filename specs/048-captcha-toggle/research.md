# Research: 验证码临时关闭模块

**Branch**: `048-captcha-toggle` | **Date**: 2026-08-25

## 1. 关闭方式

**Decision**: 改 `application.yml` 中 `crm.captcha.enabled` 默认值 true→false。AuthService 构造注入该配置，false 时跳过 `captchaService.validate`（017 既有逻辑，无需改代码）。

**Rationale**: 最小侵入——配置驱动，不改逻辑；环境变量 `CAPTCHA_ENABLED` 仍可覆盖（生产可强制开启）。

**Alternatives considered**: 删验证码逻辑——不可逆且生产仍需；改前端——不必要（后端不校验即可）。

## 2. 测试影响

**Decision**: 既有 AuthIT 登录辅助方法若需验证码则需适配（直接用户名/密码登录）。检查并更新。

**Rationale**: 关闭后测试不再需要 Redis 取验证码答案，登录流程简化。
