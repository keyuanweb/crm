import { expect, test } from '@playwright/test'

// 端到端测试（081-role-permissions-update）：需要后端已启动（8081）且 MySQL/Redis 可用。

test.describe('角色权限 E2E 测试', () => {
  test('管理员角色应看到所有菜单', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('用户名').fill('admin')
    await page.getByLabel('密码').fill('admin123')
    await page.getByRole('button', { name: /登\s*录/ }).click()
    await expect(page).toHaveURL(/\/customers/)

    // 验证管理员看到所有菜单组
    await expect(page.locator('.ant-menu-submenu')).toHaveCountGreaterThanOrEqual(8)
  })

  test('角色管理页面加载', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('用户名').fill('admin')
    await page.getByLabel('密码').fill('admin123')
    await page.getByRole('button', { name: /登\s*录/ }).click()
    await page.goto('/roles')
    await expect(page).toHaveURL(/\/roles/)
  })

  test('用户管理页面加载', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('用户名').fill('admin')
    await page.getByLabel('密码').fill('admin123')
    await page.getByRole('button', { name: /登\s*录/ }).click()
    await page.goto('/users')
    await expect(page).toHaveURL(/\/users/)
  })

  test('部门管理页面加载', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('用户名').fill('admin')
    await page.getByLabel('密码').fill('admin123')
    await page.getByRole('button', { name: /登\s*录/ }).click()
    await page.goto('/departments')
    await expect(page).toHaveURL(/\/departments/)
  })

  test('字段权限页面加载', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('用户名').fill('admin')
    await page.getByLabel('密码').fill('admin123')
    await page.getByRole('button', { name: /登\s*录/ }).click()
    await page.goto('/field-permissions')
    await expect(page).toHaveURL(/\/field-permissions/)
  })

  test('审计日志页面加载', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('用户名').fill('admin')
    await page.getByLabel('密码').fill('admin123')
    await page.getByRole('button', { name: /登\s*录/ }).click()
    await page.goto('/audit-logs')
    await expect(page).toHaveURL(/\/audit-logs/)
  })

  test('回收站页面加载', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('用户名').fill('admin')
    await page.getByLabel('密码').fill('admin123')
    await page.getByRole('button', { name: /登\s*录/ }).click()
    await page.goto('/recycle-bin')
    await expect(page).toHaveURL(/\/recycle-bin/)
  })

  test('工作流页面加载', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('用户名').fill('admin')
    await page.getByLabel('密码').fill('admin123')
    await page.getByRole('button', { name: /登\s*录/ }).click()
    await page.goto('/workflows')
    await expect(page).toHaveURL(/\/workflows/)
  })

  test('SLA 策略页面加载', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('用户名').fill('admin')
    await page.getByLabel('密码').fill('admin123')
    await page.getByRole('button', { name: /登\s*录/ }).click()
    await page.goto('/sla-policies')
    await expect(page).toHaveURL(/\/sla-policies/)
  })

  test('自定义字段页面加载', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('用户名').fill('admin')
    await page.getByLabel('密码').fill('admin123')
    await page.getByRole('button', { name: /登\s*录/ }).click()
    await page.goto('/settings/custom-fields')
    await expect(page).toHaveURL(/\/settings\/custom-fields/)
  })

  test('自定义对象页面加载', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('用户名').fill('admin')
    await page.getByLabel('密码').fill('admin123')
    await page.getByRole('button', { name: /登\s*录/ }).click()
    await page.goto('/custom-objects')
    await expect(page).toHaveURL(/\/custom-objects/)
  })

  test('开放平台页面加载', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('用户名').fill('admin')
    await page.getByLabel('密码').fill('admin123')
    await page.getByRole('button', { name: /登\s*录/ }).click()
    await page.goto('/open-platform')
    await expect(page).toHaveURL(/\/open-platform/)
  })

  test('集成中心页面加载', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('用户名').fill('admin')
    await page.getByLabel('密码').fill('admin123')
    await page.getByRole('button', { name: /登\s*录/ }).click()
    await page.goto('/integration-hub')
    await expect(page).toHaveURL(/\/integration-hub/)
  })

  test('多币种页面加载', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('用户名').fill('admin')
    await page.getByLabel('密码').fill('admin123')
    await page.getByRole('button', { name: /登\s*录/ }).click()
    await page.goto('/currencies')
    await expect(page).toHaveURL(/\/currencies/)
  })

  test('销售配额页面加载', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('用户名').fill('admin')
    await page.getByLabel('密码').fill('admin123')
    await page.getByRole('button', { name: /登\s*录/ }).click()
    await page.goto('/quotas')
    await expect(page).toHaveURL(/\/quotas/)
  })

  test('定时导出页面加载', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('用户名').fill('admin')
    await page.getByLabel('密码').fill('admin123')
    await page.getByRole('button', { name: /登\s*录/ }).click()
    await page.goto('/exports/scheduled')
    await expect(page).toHaveURL(/\/exports\/scheduled/)
  })

  test('数据保留策略页面加载', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('用户名').fill('admin')
    await page.getByLabel('密码').fill('admin123')
    await page.getByRole('button', { name: /登\s*录/ }).click()
    await page.goto('/data-retention')
    await expect(page).toHaveURL(/\/data-retention/)
  })

  test('合规导出页面加载', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('用户名').fill('admin')
    await page.getByLabel('密码').fill('admin123')
    await page.getByRole('button', { name: /登\s*录/ }).click()
    await page.goto('/data-retention/compliance-export')
    await expect(page).toHaveURL(/\/data-retention\/compliance-export/)
  })
})
