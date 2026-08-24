# Tasks: 登录验证码

**Input**: Design documents from `/specs/017-login-captcha/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/auth-captcha.md

**Tests**: 章程原则四要求测试先于实现（红→绿），本功能含单元 + 集成 + 前端渲染测试。

## Phase 1: 后端测试先行（TDD 红）

- [x] T001 [P] [US1] 后端：编写 `backend/src/test/java/com/crm/service/CaptchaServiceTest.java` 单元测试——覆盖生成（返回 captchaId+非空 base64、答案写入 Redis）、校验成功（不区分大小写）、校验失败（答案不匹配抛 CAPTCHA_INVALID）、一次性（校验后 Redis key 删除）、过期/不存在（抛 CAPTCHA_EXPIRED）。此时 CaptchaService 未实现，测试编译失败（红）。
- [x] T002 [P] [US1] 后端：编写 `backend/src/test/java/com/crm/integration/AuthCaptchaIT.java` 集成测试——GET /auth/captcha 返回 200 + captchaId + imageBase64；用返回的 captchaId 登录成功；错误验证码登录返回 422；空验证码登录返回 422。此时接口未实现，测试失败（红）。

## Phase 2: 后端实现

- [x] T003 [P] [US1] 后端：`ErrorCode.java` 新增 `CAPTCHA_INVALID(422, "CAPTCHA_INVALID", "验证码错误")` 与 `CAPTCHA_EXPIRED(422, "CAPTCHA_EXPIRED", "验证码已过期，请重新输入")`。
- [x] T004 [P] [US1] 后端：新增 `dto/auth/CaptchaResponse.java`（captchaId + imageBase64）；`dto/auth/LoginRequest.java` 新增 `captchaId`、`captchaCode` 字段（@NotBlank 校验在 service 层按序判断，DTO 保持宽松以便区分错误类型）。
- [x] T005 [US1] 后端：新增 `service/CaptchaService.java`——`generate()` 生成 UUID + 4~6 位大写答案（字符集 A-Z、2-9，排除易混淆）+ AWT 图形验证码图片 base64（data URL），答案写入 Redis（`auth:captcha:<id>`，TTL 300s）；`validate(captchaId, captchaCode)` 取答案、空/不存在→CAPTCHA_EXPIRED、不匹配（忽略大小写）→CAPTCHA_INVALID，成功后 DEL key（一次性）；生成/校验失败记 SLF4J 日志。
- [x] T006 [US1] 后端：`AuthController.java` 新增 `GET /auth/captcha`（返回 ApiResponse<CaptchaResponse>）；`SecurityConfig.java` 将 `/api/v1/auth/captcha` 加入 permitAll。
- [x] T007 [US1] 后端：`AuthService.login()` 入口先调用 `captchaService.validate()`（验证码错误不触发 recordFailure），验证码通过后走原有凭证逻辑。（依赖 T005）

## Phase 3: 前端测试先行（TDD 红）

- [x] T008 [P] [US1] 前端：更新 `frontend/src/pages/LoginPage.test.tsx`——新增用例：渲染验证码图片与输入框；点击图片触发刷新（fetchCaptcha 被再次调用）；提交时携带 captchaId/captchaCode；验证码错误时显示错误并刷新验证码。mock `authService.fetchCaptcha`。

## Phase 4: 前端实现

- [x] T009 [US1] 前端：`services/authService.ts` 新增 `fetchCaptcha()` 返回 `{ captchaId, imageBase64 }`；`login()` 签名扩展为 `(username, password, captchaId, captchaCode)`，请求体携带 captcha 字段。
- [x] T010 [US1] 前端：`pages/LoginPage.tsx` 新增验证码输入框（用户名/密码之间）+ 验证码图片（`<img>` 点击刷新，用 imageBase64 data URL）；`onFinish` 携带 captcha 字段；登录失败时清空验证码输入并刷新验证码；页面加载与刷新时自动拉取验证码。（依赖 T009）

## Phase 5: 验证与收尾

- [x] T011 后端：`mvn test` 全量通过（含新增 CaptchaServiceTest + AuthCaptchaIT，不影响既有 198 测试）。
- [x] T012 前端：`pnpm run typecheck` + `pnpm run lint` + `pnpm run test` 全量通过（含更新后的 LoginPage.test.tsx）。
- [x] T013 [P] 手动冒烟：登录页显示验证码；错误验证码被拒并刷新；正确验证码 + admin/admin123 登录成功进入首页。

## Dependencies & Execution Order

- T001/T002（后端测试）与 T008（前端测试）可并行，均为红阶段。
- T003/T004 可并行；T005 依赖 T003/T004；T006/T007 依赖 T005。
- T009 依赖无；T010 依赖 T009。
- Phase 5 在所有实现完成后执行。

## Notes

- 验证码图片用 JDK AWT 生成，不引入第三方依赖（YAGNI）。
- 验证码一次性语义：validate 内先 GET 后 DEL。
- 验证码错误不累计登录失败次数（FR-007）。
- 测试环境（application-test.yml）默认 `crm.captcha.enabled=false`，避免影响大量依赖 `loginAndGetToken` 的既有集成测试；AuthCaptchaIT 用 `@TestPropertySource(properties="crm.captcha.enabled=true")` 单独启用验证码。
