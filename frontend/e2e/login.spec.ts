import { expect, test } from '@playwright/test'

// 端到端冒烟（quickstart S1~S2，antd 版）：需要后端已启动（8081）且 MySQL/Redis 可用。
test('登录并完成客户创建流程', async ({ page }) => {
  await page.goto('/login')

  await page.getByLabel('用户名').fill('admin')
  await page.getByLabel('密码').fill('admin123')
  await page.getByRole('button', { name: /登\s*录/ }).click()

  await expect(page).toHaveURL(/\/customers/)

  // 新增客户（ProTable 工具栏按钮 + 弹窗表单；限定在 .ant-modal 内避免与搜索表单同名 label 冲突）
  await page.getByRole('button', { name: '新增客户' }).click()
  const modal = page.locator('.ant-modal', { hasText: '新增客户' })
  await modal.getByLabel('客户名称').fill('E2E 客户')
  await modal.getByLabel('公司').fill('E2E 公司')
  await modal.getByRole('button', { name: /保\s*存/ }).click()

  await expect(page.getByText('E2E 客户')).toBeVisible()
})

test('登录页面加载', async ({ page }) => {
  await page.goto('/login')
  await expect(page).toHaveTitle(/CRM/)
})

test('导航菜单显示', async ({ page }) => {
  await page.goto('/dashboard')
  await expect(page.locator('.ant-menu-item')).toBeVisible()
})

test('商机列表页面加载', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('用户名').fill('admin')
  await page.getByLabel('密码').fill('admin123')
  await page.getByRole('button', { name: /登\s*录/ }).click()
  await page.goto('/opportunities')
  await expect(page).toHaveURL(/\/opportunities/)
})

