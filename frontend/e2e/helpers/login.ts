import { expect, type Page } from '@playwright/test'

/**
 * 登录并等待跳转到工作台（各用例的统一登录前置）。
 *
 * **关于验证码**：登录页恒渲染「验证码」输入框且 UI 侧为必填，而后端在
 * `crm.captcha.enabled=false`（`application.yml` 的默认值）时**完全不校验**该字段
 * ——`AuthService.login` 仅在 `captchaEnabled` 为真时才 `captchaService.validate`。
 * 故此处填占位值即可通过前端校验，服务端不比对内容。
 *
 * 这也解释了本套件此前的状态：用例只填了用户名/密码，被前端必填校验挡住、请求根本没发出，
 * 22/23 用例长期全红（`048` 引入验证码之后即如此，且从未被执行过）。
 *
 * **前置条件**：E2E 目标环境的验证码必须为关闭状态（默认即是）。若以
 * `CAPTCHA_ENABLED=true` 启动，本套件无法自动通过——该约束已写入 quickstart。
 */
export async function login(page: Page, username = 'admin', password = 'admin123') {
  await page.goto('/login')
  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('密码').fill(password)
  // exact：否则会同时命中验证码图片的 aria-label「验证码图片」（strict mode 冲突）
  await page.getByLabel('验证码', { exact: true }).fill('0000')
  await page.getByRole('button', { name: /登\s*录/ }).click()
  // 登录后落到的是首页（统计仪表盘），由 App.tsx 的
  // `<Route index element={<Navigate to="/stats" replace />} />` 决定。
  // 原用例断言 /customers，是落地路由变更后遗留的陈旧断言，同样从未通过。
  await expect(page).toHaveURL(/\/stats/)
}
