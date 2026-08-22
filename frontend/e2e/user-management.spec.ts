import { expect, test } from '@playwright/test'

// 端到端：用户管理流程（T020，002-user-management）
// 前置：后端已启动（8081）+ MySQL/Redis 可用；管理员 admin/admin123 存在。
test('管理员创建销售用户，新用户可登录', async ({ page }) => {
  const suffix = Date.now().toString(36)
  const username = `e2e_${suffix}`
  const password = 'pass1234'

  // 管理员登录
  await page.goto('/login')
  await page.getByLabel('用户名').fill('admin')
  await page.getByLabel('密码').fill('admin123')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/customers/)

  // 进入用户管理（仅 ADMIN 可见）
  await page.getByRole('link', { name: '用户管理' }).click()
  await expect(page).toHaveURL(/\/users/)

  // 创建用户（FR-003）
  await page.getByRole('button', { name: '新增用户' }).click()
  await page.getByLabel(/用户名/).fill(username)
  await page.getByLabel(/显示名/).fill('E2E 用户')
  await page.getByLabel(/初始密码/).fill(password)
  await page.getByRole('button', { name: '创建' }).click()

  // 列表出现新用户
  await expect(page.getByRole('cell', { name: username })).toBeVisible()

  // 退出后新用户登录（FR-001 验收 3）
  await page.getByRole('button', { name: '退出' }).click()
  await page.goto('/login')
  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('密码').fill(password)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/customers/)

  // 新用户不应看到"用户管理"入口（FR-008）
  await expect(page.getByRole('link', { name: '用户管理' })).toHaveCount(0)
})
