import { expect, test } from '@playwright/test'
import { login } from './helpers/login'

// 端到端冒烟（quickstart S1~S2，antd 版）：需要后端已启动（8081）且 MySQL/Redis 可用。
test('登录并完成客户创建流程', async ({ page }) => {
  await login(page)

  // 落地页是首页（/stats），客户创建在客户列表页
  await page.goto('/customers')

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
  // 原实现直接 goto('/dashboard')：该路由在 App.tsx 中不存在，未登录时会被守卫重定向到 /login，
  // 断言「页面上有菜单项」必然落空——该用例从未通过过。改为登录后断言菜单。
  await login(page)
  await expect(page.locator('.ant-menu-item').first()).toBeVisible()
})

test('商机列表页面加载', async ({ page }) => {
  await login(page)
  await page.goto('/opportunities')
  await expect(page).toHaveURL(/\/opportunities/)
})
