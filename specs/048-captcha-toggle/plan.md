# Implementation Plan: 验证码临时关闭模块

**Branch**: `048-captcha-toggle` | **Date**: 2026-08-25 | **Spec**: [spec.md](./spec.md)

## Summary

将登录验证码默认关闭：`application.yml` 中 `crm.captcha.enabled` 默认值由 true 改为 false。AuthService 既有逻辑在关闭时跳过校验；`CAPTCHA_ENABLED` 环境变量可随时重新开启。

## Technical Context

**Language/Version**: Java 17（沿用）

**Primary Dependencies**: 无新增（017 开关机制已存在）

**Storage**: 无

**Testing**: 后端既有 AuthIT 需适配（登录不再需要验证码）；verify 通过

**Target Platform**: Web（登录流程）

**Project Type**: 配置变更

**Constraints**: 不删 `GET /auth/captcha` 接口；保留环境变量覆盖

**Scale/Scope**: 单文件配置默认值

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则五：简洁、可维护与可观测 | 最小变更、可配置 | ✅ 满足（复用既有开关） |

**结论**: 无门禁违规。

## Project Structure

```text
specs/048-captcha-toggle/
├── spec.md / plan.md / tasks.md / research.md / quickstart.md
└── checklists/requirements.md

backend/src/main/resources/application.yml  # crm.captcha.enabled 默认 true→false
```

**Structure Decision**: 仅改默认值；AuthService/AuthController/CaptchaService 不动（开关已支持）。

## Complexity Tracking

无违规，本表留空。
