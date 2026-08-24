# 快速开始：登录验证码

## 后端

1. 新增 `CaptchaService`：生成验证码（UUID + 随机答案 + AWT 图片 base64）、校验（Redis 取答案、不区分大小写比对、DEL 一次性）。
2. `AuthController` 新增 `GET /api/v1/auth/captcha`。
3. `LoginRequest` 新增 `captchaId`、`captchaCode` 字段。
4. `AuthService.login` 入口先校验验证码，再走原有凭证逻辑。
5. `SecurityConfig` 将 `/api/v1/auth/captcha` 加入 permitAll。
6. `ErrorCode` 新增 `CAPTCHA_INVALID`、`CAPTCHA_EXPIRED`。

## 前端

1. `authService.ts` 新增 `fetchCaptcha()`，`login()` 签名扩展 captcha 参数。
2. `LoginPage.tsx` 新增验证码输入框 + 图片（点击刷新）；登录失败时刷新验证码。
3. 验证码图片用接口返回的 `imageBase64`（data URL）直接作为 `<img src>`。

## 验证

- 后端：`mvn test`（CaptchaServiceTest 单元 + AuthCaptchaIT 集成）。
- 前端：`pnpm run test`（LoginPage 验证码渲染/刷新/提交）。
- 手动：登录页输入错误验证码应被拒并刷新；正确验证码 + admin/admin123 可登录。
