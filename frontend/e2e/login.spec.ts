import { expect, test } from '@playwright/test'

// 端到端冒烟（quickstart S1~S2）：需要后端已启动（mvn spring-boot:run）且 MySQL/Redis 可用。
test('登录并完成客户创建流程', async ({ page }) => {
  await page.goto('/login')

  await page.getByLabel('用户名').fill('admin')
  await page.getByLabel('密码').fill('admin123')
  await page.getByRole('button', { name: '登录' }).click()

  await expect(page).toHaveURL(/\/customers/)

  // 新增客户
  await page.getByRole('button', { name: '新增客户' }).click()
  await page.getByLabel('客户名称').fill('E2E 客户')
  await page.getByLabel('公司').fill('E2E 公司')
  await page.getByRole('button', { name: '保存' }).click()

  await expect(page.getByText('E2E 客户')).toBeVisible()
})
