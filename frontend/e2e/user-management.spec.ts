import { expect, test } from '@playwright/test'
import { login } from './helpers/login'

// 端到端：用户管理流程（T020，antd 版）
// 前置：后端已启动（8081）+ MySQL/Redis 可用；管理员 admin/admin123 存在。
test('管理员创建销售用户，新用户可登录', async ({ page }) => {
  const suffix = Date.now().toString(36)
  const username = `e2e_${suffix}`
  const password = 'pass1234'

  // 管理员登录
  await login(page)

  // 进入用户管理（仅 ADMIN 可见）。042 起侧栏按分组折叠，须先展开「系统管理」
  // （原实现直接找顶层 link「用户管理」，在折叠菜单下永远找不到）
  await page.getByRole('menuitem', { name: /系统管理/ }).click()
  await page.getByRole('menuitem', { name: '用户管理' }).click()
  await expect(page).toHaveURL(/\/users/)

  // 创建用户（FR-003；限定在 .ant-modal 内避免与搜索表单同名 label 冲突）
  await page.getByRole('button', { name: '新增用户' }).click()
  const modal = page.locator('.ant-modal', { hasText: '新增用户' })
  await modal.getByLabel('用户名').fill(username)
  await modal.getByLabel('显示名').fill('E2E 用户')
  await modal.getByLabel('初始密码').fill(password)
  await modal.getByRole('button', { name: /创\s*建/ }).click()
  await expect(modal).toBeHidden()

  // 列表出现新用户——**先翻到最后一页**。
  // 用户列表按 id **升序**分页（`UserService.page` 的 `orderByAsc(User::getId)`），每页 20 条，
  // 故新建用户（id 最大）恒在**最后一页**。原断言直接在首页找它，只在"库里用户总数不足一页"时成立——
  // 累计用户超过 20（种子 + 历次 e2e 运行留下的用户，实测 26）后必然失败。
  // 这是"数据量一变就红"的定时炸弹，与 T017 归因的 ③ 类测试腐化同族。
  // 不改为填写搜索框：该页 ProTable 的 `request` 只转发 `keyword`／`role`，而用户名搜索列产生的是
  // `username` 参数——搜索框当前是**失效的**（已另记为缺陷，不在本规格内顺手修）。
  await page.locator('.ant-pagination-item').last().click()
  await expect(page.getByRole('cell', { name: username })).toBeVisible()

  // 通过头像下拉退出（旧版"退出"按钮已被 ProLayout 头像下拉取代）。
  // 触发器是顶栏头像本身（只渲染显示名首字，没有可访问名称），
  // 原用例按文本「系统管理员（ADMIN）」定位，在 025 改版后已无法命中。
  await page.getByRole('banner').locator('.ant-avatar').click()
  await page.getByRole('menuitem', { name: '退出登录' }).click()
  await expect(page).toHaveURL(/\/login/)

  // 新用户登录（FR-001 验收 3）
  await login(page, username, password)

  // 新用户不应看到"用户管理"入口（FR-008）。
  // 分组折叠时断言「找不到该入口」会恒真（假绿），故先展开「系统管理」（若该分组对其可见——
  // 该分组仅在用户对组内至少一项有权限时才渲染，见 App.tsx 的 filterByMenus）。
  const adminGroup = page.getByRole('menuitem', { name: /系统管理/ })
  if (await adminGroup.count()) {
    await adminGroup.click()
  }
  await expect(page.getByRole('menuitem', { name: '用户管理' })).toHaveCount(0)
})
