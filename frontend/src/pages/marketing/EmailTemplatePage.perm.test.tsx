import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import EmailTemplatePage from './EmailTemplatePage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/emailService', () => ({
  fetchEmailTemplates: vi.fn(),
  createEmailTemplate: vi.fn(),
  updateEmailTemplate: vi.fn(),
  deleteEmailTemplate: vi.fn(),
}))

/**
 * 086 权限收口 · `EmailTemplatePage` 行内「删除」按钮的渲染测试。
 *
 * <p>判据是 `email:manage`（`EmailController` 全线只有这一个写码，删除模板走
 * `DELETE /email-templates/{id}`）。「预览」「编辑」不在收口范围内，保持无条件渲染——
 * 这里也顺带断言它们仍在，防止收口时手抖把整列包进去。
 *
 * <p>负向用例**必须**用非 ADMIN：`hasPerm` 对 `role === 'ADMIN'` 直接短路
 * （`hooks/usePermission.ts:10`），管理员永远拿不到"看不见"的结论。
 */
const templateRow = {
  id: 1,
  name: '客户欢迎',
  subject: '欢迎 {name}',
  content: '<p>尊敬的 {name}，您好</p>',
  category: 'WELCOME',
}

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 只多了 `email:manage`，其余逐字相同。 */
const salesWithPerm: UserInfo = { ...salesNoPerm, permissions: ['email:manage'] }

const queryDelete = () => screen.queryAllByText('common.button.delete')

async function renderPage(user: UserInfo) {
  useAuthStore.setState({ user })
  const { fetchEmailTemplates } = await import('../../services/emailService')
  vi.mocked(fetchEmailTemplates).mockResolvedValue([templateRow] as never)

  renderWithProviders(<EmailTemplatePage />)
  // 等到表格真的渲染出这一行，后续的否定断言才有意义 ——
  // 否则"按钮不在"可能只是"页面还没加载出来"，那样的负向断言是假绿。
  await screen.findByText('客户欢迎')
}

/** 反空洞守卫：模板行与两个未收口的链接都真的渲染了。 */
function expectRowRendered() {
  expect(screen.getByText('客户欢迎')).toBeInTheDocument()
  expect(screen.getByText('pages.marketing.emailTemplate.btnPreview')).toBeInTheDocument()
  expect(screen.getByText('common.button.edit')).toBeInTheDocument()
}

describe('EmailTemplatePage 权限收口（086）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('ADMIN 看得见行内「删除」', async () => {
    await renderPage(adminUser)

    expectRowRendered()
    expect(queryDelete()).toHaveLength(1)
  })

  it('无 email:manage 的 SALES：行渲染出来了，但「删除」不在', async () => {
    await renderPage(salesNoPerm)

    expectRowRendered()
    expect(queryDelete()).toHaveLength(0)
  })

  it('持有 email:manage 的 SALES：看得见「删除」（该权限从此可授予）', async () => {
    await renderPage(salesWithPerm)

    expectRowRendered()
    expect(queryDelete()).toHaveLength(1)
  })
})
