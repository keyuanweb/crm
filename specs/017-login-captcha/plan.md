# Implementation Plan: 登录验证码

**Branch**: `017-login-captcha` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/017-login-captcha/spec.md`

## Summary

在登录入口新增图形验证码：登录页展示服务端生成的 4~6 位字母数字图形验证码，用户提交登录时必须一并提交验证码；验证码错误/过期/已使用均拒绝登录。验证码答案临时存 Redis（TTL 5 分钟），一次性使用（校验后删除），复用现有认证服务，不改变令牌签发逻辑。

## Technical Context

**Language/Version**: Java 17（后端）+ TypeScript/React 18（前端）

**Primary Dependencies**: Spring Boot 3.2（Spring Security / Spring Data Redis）、MyBatis-Plus、antd 5 + @ant-design/pro-components；验证码图片用 JDK 内置 `java.awt`（BufferedImage/Graphics2D）生成，不引入第三方验证码库（YAGNI）

**Storage**: Redis（临时验证码答案，`auth:captcha:<id>` → 答案，TTL 300s）；不新增数据库表

**Testing**: JUnit 5 + Spring Boot Test + Mockito（后端）；Vitest + React Testing Library（前端）

**Target Platform**: Web（现代浏览器）

**Project Type**: Web 应用（Spring Boot 后端 + React 前端）

**Performance Goals**: 验证码接口 < 50ms；登录校验无额外可感知延迟

**Constraints**: 无状态 JWT（不用 HTTP session 存验证码，用 Redis + captchaId 关联）；验证码不区分大小写、不含易混淆字符；验证码错误不累计登录失败次数

**Scale/Scope**: 单登录入口，1 个新接口 + 登录接口扩展 + 登录页 1 处 UI 改动

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则一：契约优先的 API 设计 | 端点契约先于实现 | ✅ 满足（contracts/auth-captcha.md 先定义 captcha 端点与 login 扩展） |
| 原则二：分层架构与关注点分离 | Controller→Service→Repository | ✅ 满足（CaptchaService 生成/校验，AuthController 只加 captcha 端点，AuthService 只做校验调用） |
| 原则三：数据完整性、安全与校验 | DTO 校验、服务端强制 | ✅ 满足（CaptchaService 在服务端校验；验证码答案不落日志/不落库） |
| 原则四：测试优先与质量门禁 | 测试先于实现 | ✅ 满足（CaptchaServiceTest 单元 + AuthIT 集成 + LoginPage 渲染测试） |
| 原则五：简洁、可维护与可观测 | YAGNI、结构化日志 | ✅ 满足（不引入验证码库，用 JDK AWT；验证码生成/校验失败记 SLF4J 日志） |

**结论**: 无门禁违规。

## Project Structure

### Documentation (this feature)

```text
specs/017-login-captcha/
├── plan.md              # 本文件
├── research.md          # Phase 0 输出（技术选型与决策记录）
├── data-model.md        # Phase 1 输出（验证码会话数据模型）
├── quickstart.md        # Phase 1 输出
├── contracts/           # Phase 1 输出（auth-captcha 契约）
└── tasks.md             # Phase 2 输出（/speckit-tasks）
```

### Source Code (repository root)

```text
backend/src/main/java/com/crm/
├── dto/auth/CaptchaResponse.java        # 新增：{ captchaId, imageBase64 }
├── dto/auth/LoginRequest.java           # 修改：新增 captchaId + captchaCode
├── service/CaptchaService.java          # 新增：生成/校验验证码
├── controller/AuthController.java       # 修改：新增 GET /auth/captcha
├── config/SecurityConfig.java           # 修改：/auth/captcha 加入 permitAll
└── common/ErrorCode.java                # 修改：新增 CAPTCHA_INVALID / CAPTCHA_EXPIRED

backend/src/test/java/com/crm/
├── service/CaptchaServiceTest.java      # 新增：生成/校验/一次性/过期 单元测试
└── integration/AuthCaptchaIT.java       # 新增：captcha→login 端到端集成测试

frontend/src/
├── services/authService.ts              # 修改：fetchCaptcha + login 传 captcha 字段
├── pages/LoginPage.tsx                  # 修改：验证码输入框 + 图片刷新
└── pages/LoginPage.test.tsx             # 修改：验证码渲染/刷新/提交校验
```

**Structure Decision**: 沿用既有前后端分层。验证码图片用 base64 内联返回（避免 cookie/额外图片请求的会话关联复杂度）；验证码会话数据仅在 Redis 内存存，无持久化实体，故无 Flyway 迁移。

## Complexity Tracking

> 无违规，本表留空。
