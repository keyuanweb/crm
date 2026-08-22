import { expect, test } from '@playwright/test'

// 端到端：用户管理流程（T020，antd 版）
// 前置：后端已启动（8081）+ MySQL/Redis 可用；管理员 admin/admin123 存在。
test('管理员创建销售用户，新用户可登录', async ({ page }) => {
  const suffix = Date.now().toString(36)
  const username = `e2e_${suffix}`
  const password = 'pass1234'

  // 管理员登录
  await page.goto('/login')
  await page.getByLabel('用户名').fill('admin')
  await page.getByLabel('密码').fill('admin123')
  await page.getByRole('button', { name: /登\s*录/ }).click()
  await expect(page).toHaveURL(/\/customers/)

  // 进入用户管理（仅 ADMIN 可见）
  await page.getByRole('link', { name: '用户管理' }).click()
  await expect(page).toHaveURL(/\/users/)

  // 创建用户（FR-003；限定在 .ant-modal 内避免与搜索表单同名 label 冲突）
  await page.getByRole('button', { name: '新增用户' }).click()
  const modal = page.locator('.ant-modal', { hasText: '新增用户' })
  await modal.getByLabel('用户名').fill(username)
  await modal.getByLabel('显示名').fill('E2E 用户')
  await modal.getByLabel('初始密码').fill(password)
  await modal.getByRole('button', { name: /创\s*建/ }).click()

  // 列表出现新用户
  await expect(page.getByRole('cell', { name: username })).toBeVisible()

  // 通过头像下拉退出（旧版"退出"按钮已被 ProLayout 头像下拉取代）
  await page.getByText('系统管理员（ADMIN）').click()
  await page.getByText('退出登录').click()
  await expect(page).toHaveURL(/\/login/)

  // 新用户登录（FR-001 验收 3）
  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('密码').fill(password)
  await page.getByRole('button', { name: /登\s*录/ }).click()
  await expect(page).toHaveURL(/\/customers/)

  // 新用户不应看到"用户管理"入口（FR-008）
  await expect(page.getByRole('link', { name: '用户管理' })).toHaveCount(0)
})
