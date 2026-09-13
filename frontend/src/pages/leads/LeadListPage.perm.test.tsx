import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import LeadListPage from './LeadListPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/leadService', () => ({
  fetchLeads: vi.fn(),
  fetchLead: vi.fn(),
  createLead: vi.fn(),
  updateLead: vi.fn(),
  deleteLead: vi.fn(),
  assignLead: vi.fn(),
  claimLead: vi.fn(),
  convertLead: vi.fn(),
  importLeads: vi.fn(),
  downloadLeadTemplate: vi.fn(),
}))
vi.mock('../../services/marketingService', () => ({
  fetchCampaigns: vi.fn(async () => ({ items: [], total: 0, page: 1, pageSize: 100 })),
  createCampaign: vi.fn(),
  updateCampaign: vi.fn(),
  startCampaign: vi.fn(),
  endCampaign: vi.fn(),
  deleteCampaign: vi.fn(),
  fetchChannelRoi: vi.fn(),
}))
vi.mock('../../services/customFieldService', () => ({
  fetchFieldDefinitions: vi.fn(async () => []),
  fetchCustomFields: vi.fn(),
  createCustomField: vi.fn(),
  updateCustomField: vi.fn(),
  deleteCustomField: vi.fn(),
}))

/**
 * 086 权限收口 · `LeadListPage` 行内「删除」的渲染测试。
 *
 * <p>删除线索走 `DELETE /leads/{id}`，`LeadController` 上标的是 `lead:delete`。
 * 这一处是**改造前完全没有判据**的一处（`const canDelete` 是 086 才加的），
 * 所以三条断言的方向要写全：ADMIN 可见 / 零码非 ADMIN 不可见 / 持码非 ADMIN 可见。
 * 第三条尤其关键——它证明了这个码真的能授予，而不只是"把按钮藏起来"。
 *
 * <p><b>刻意不测同页的 `canAssign`</b>：那是既有的判据，不属于本次收口范围，
 * 改了它或者给它加断言都会把本文件的"失败"含义弄混。
 *
 * <p>负向用例另加一条**同行对照**：同处操作列的「编辑」链接在无码用户下**仍须在**
 * （线索状态为 NEW 时 `canEdit` 为真）。它比"页面上有某行数据"更贴题——
 * 证明该行、该操作列、该渲染分支都跑到了，消失的只有被收口的那一个链接。
 *
 * <p>负向用例**必须**用非 ADMIN：`hasPerm` 对 `role === 'ADMIN'` 直通返回 true
 * （`hooks/usePermission.ts:10`），管理员永远测不出「看不见」。
 */
const leadRow = {
  id: 1,
  name: '线索甲',
  company: 'Acme 科技',
  title: '采购经理',
  phone: '13800000000',
  email: 'jia@acme.com',
  source: 'WEBSITE',
  // NEW 才能让「编辑」与「删除」同时进入渲染分支（QUALIFIED/DISQUALIFIED 会把两个都关掉）
  status: 'NEW',
  score: 80,
  // ownerId 非空且 ≠ 当前用户：避开「认领」与「分配给我」两支，只留本文件要测的控件
  ownerId: 99,
  version: 0,
}

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码——"看不见"的对照组必须建立在它之上。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 只多了 `lead:delete`，其余与基线逐字相同——把差异收敛到唯一变量。 */
const salesWithDelete: UserInfo = { ...salesNoPerm, permissions: ['lead:delete'] }

const withUser = (user: UserInfo) => useAuthStore.setState({ user })

/** 行内「删除」链接（`DeleteOutlined` 是 svg 不含文本，故 `<a>` 的可见文本恰是这个键名）。 */
const queryDelete = () => screen.queryByText('pages.lead.list.delete')
/** 行内「编辑」链接——无码用例里用它做同行对照，证明操作列本身渲染到位。 */
const queryEdit = () => screen.queryByText('pages.lead.list.edit')

async function renderPage(user: UserInfo) {
  withUser(user)
  const { fetchLeads } = await import('../../services/leadService')
  vi.mocked(fetchLeads).mockResolvedValue({ items: [leadRow], total: 1, page: 1, pageSize: 20 } as never)
  renderWithProviders(<LeadListPage />)
  // 等到表格真的渲染出这一行，后续的否定断言才有意义 ——
  // 否则"链接不在"可能只是"页面还没加载出来"，那样的负向断言是假绿。
  await screen.findByText('线索甲')
}

describe('LeadListPage 行内删除的权限收口（086）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('ADMIN 看得见行内「删除」', async () => {
    await renderPage(adminUser)

    expect(queryDelete()).toBeInTheDocument()
  })

  it('无 lead:delete 的 SALES 看不见行内「删除」，但同行的「编辑」仍在', async () => {
    await renderPage(salesNoPerm)

    // 反空洞守卫：该行确实渲染了，且操作列本身跑到了——消失的只有被收口的那个链接
    expect(screen.getByText('线索甲')).toBeInTheDocument()
    expect(queryEdit()).toBeInTheDocument()
    expect(queryDelete()).not.toBeInTheDocument()
  })

  it('持有 lead:delete 的 SALES 看得见行内「删除」（该权限从此可授予）', async () => {
    await renderPage(salesWithDelete)

    expect(queryDelete()).toBeInTheDocument()
  })
})
