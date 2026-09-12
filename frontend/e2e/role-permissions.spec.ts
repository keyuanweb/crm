import { expect, test } from '@playwright/test'
import { login } from './helpers/login'

// 端到端测试（081-role-permissions-update）：需要后端已启动（8081）且 MySQL/Redis 可用。
// 登录前置统一走 helpers/login——原实现把 4 行登录序列在 18 个用例里各抄一遍，
// 048 给登录页加必填验证码后这 18 份副本同时失效（请求根本没发出），整套长期全红。

test.describe('角色权限 E2E 测试', () => {
  // 每个用例是独立的浏览器上下文，登录态不共享，故逐个登录
  test.beforeEach(async ({ page }) => {
    await login(page)
  })

  test('管理员角色应看到所有菜单', async ({ page }) => {
    // 验证管理员看到所有菜单组。
    // 原实现用 `expect(locator).toHaveCountGreaterThanOrEqual(8)`——Playwright 没有这个匹配器，
    // 该断言一执行就抛 TypeError，从未真正校验过任何东西。下界断言改用 expect.poll（可自动重试）。
    await expect.poll(() => page.locator('.ant-menu-submenu').count()).toBeGreaterThanOrEqual(8)
  })

  test('角色管理页面加载', async ({ page }) => {
    await page.goto('/roles')
    await expect(page).toHaveURL(/\/roles/)
  })

  test('用户管理页面加载', async ({ page }) => {
    await page.goto('/users')
    await expect(page).toHaveURL(/\/users/)
  })

  test('部门管理页面加载', async ({ page }) => {
    await page.goto('/departments')
    await expect(page).toHaveURL(/\/departments/)
  })

  test('字段权限页面加载', async ({ page }) => {
    await page.goto('/field-permissions')
    await expect(page).toHaveURL(/\/field-permissions/)
  })

  test('审计日志页面加载', async ({ page }) => {
    await page.goto('/audit-logs')
    await expect(page).toHaveURL(/\/audit-logs/)
  })

  test('回收站页面加载', async ({ page }) => {
    await page.goto('/recycle-bin')
    await expect(page).toHaveURL(/\/recycle-bin/)
  })

  test('工作流页面加载', async ({ page }) => {
    await page.goto('/workflows')
    await expect(page).toHaveURL(/\/workflows/)
  })

  test('SLA 策略页面加载', async ({ page }) => {
    await page.goto('/sla-policies')
    await expect(page).toHaveURL(/\/sla-policies/)
  })

  test('自定义字段页面加载', async ({ page }) => {
    await page.goto('/settings/custom-fields')
    await expect(page).toHaveURL(/\/settings\/custom-fields/)
  })

  test('自定义对象页面加载', async ({ page }) => {
    await page.goto('/custom-objects')
    await expect(page).toHaveURL(/\/custom-objects/)
  })

  test('开放平台页面加载', async ({ page }) => {
    await page.goto('/open-platform')
    await expect(page).toHaveURL(/\/open-platform/)
  })

  test('集成中心页面加载', async ({ page }) => {
    await page.goto('/integration-hub')
    await expect(page).toHaveURL(/\/integration-hub/)
  })

  test('多币种页面加载', async ({ page }) => {
    await page.goto('/currencies')
    await expect(page).toHaveURL(/\/currencies/)
  })

  test('销售配额页面加载', async ({ page }) => {
    await page.goto('/quotas')
    await expect(page).toHaveURL(/\/quotas/)
  })

  test('定时导出页面加载', async ({ page }) => {
    await page.goto('/exports/scheduled')
    await expect(page).toHaveURL(/\/exports\/scheduled/)
  })

  test('数据保留策略页面加载', async ({ page }) => {
    await page.goto('/data-retention')
    await expect(page).toHaveURL(/\/data-retention/)
  })

  test('合规导出页面加载', async ({ page }) => {
    await page.goto('/data-retention/compliance-export')
    await expect(page).toHaveURL(/\/data-retention\/compliance-export/)
  })
})
