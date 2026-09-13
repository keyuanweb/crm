import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import ContactListPage from './ContactListPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/contactService', () => ({
  fetchContacts: vi.fn(),
  fetchContact: vi.fn(),
  createContact: vi.fn(),
  updateContact: vi.fn(),
  deleteContact: vi.fn(),
  importContacts: vi.fn(),
  downloadContactTemplate: vi.fn(),
}))
vi.mock('../../services/customerService', () => ({
  fetchCustomers: vi.fn(async () => ({ items: [], total: 0, page: 1, pageSize: 50 })),
  fetchCustomer: vi.fn(),
  fetchAtRiskCustomers: vi.fn(),
  createCustomer: vi.fn(),
  updateCustomer: vi.fn(),
  deleteCustomer: vi.fn(),
  fetchPoolCustomers: vi.fn(),
  fetchMyCustomers: vi.fn(),
  claimCustomer: vi.fn(),
  scanPool: vi.fn(),
  batchTransferCustomers: vi.fn(),
  importCustomers: vi.fn(),
  exportCustomers: vi.fn(),
  downloadTemplate: vi.fn(),
}))

/**
 * 086 权限收口 · `ContactListPage` 行内「删除」的渲染测试。
 *
 * <p>删除联系人走 `DELETE /contacts/{id}`，`ContactController` 上标的是 `contact:delete`，
 * 所以行内链接挂 `PERMS.contactDelete`。
 *
 * <p>三条断言缺一不可：ADMIN 可见（未误伤管理员）、零码非 ADMIN 不可见（收口生效）、
 * 持码非 ADMIN 可见（<b>证明码本身起了作用</b>——没有它，"链接不在"可能只是行没渲染出来）。
 *
 * <p>负向用例另加一条**同行对照**：同处操作列的「编辑」链接在无码用户下**仍须在**。
 * 它比"页面上有某行数据"更贴题——证明该行、该操作列、该渲染分支都跑到了，
 * 消失的只有被收口的那一个链接。
 *
 * <p>负向用例**必须**用非 ADMIN：`hasPerm` 对 `role === 'ADMIN'` 直通返回 true
 * （`hooks/usePermission.ts:10`），管理员永远测不出「看不见」。
 */
const contactRow = {
  id: 1,
  customerId: 5,
  customerName: 'Acme 科技',
  name: '张三',
  title: '采购经理',
  phone: '13800000000',
  email: 'zhangsan@acme.com',
  role: 'DECISION_MAKER',
  version: 0,
}

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码——"看不见"的对照组必须建立在它之上。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 只多了 `contact:delete`，其余与基线逐字相同——把差异收敛到唯一变量。 */
const salesWithDelete: UserInfo = { ...salesNoPerm, permissions: ['contact:delete'] }

const withUser = (user: UserInfo) => useAuthStore.setState({ user })

/** 行内「删除」链接（`DeleteOutlined` 是 svg 不含文本，故 `<a>` 的可见文本恰是这个键名）。 */
const queryDelete = () => screen.queryByText('pages.contact.list.delete')
/** 行内「编辑」链接——无码用例里用它做同行对照，证明操作列本身渲染到位。 */
const queryEdit = () => screen.queryByText('pages.contact.list.edit')

async function renderPage(user: UserInfo) {
  withUser(user)
  const { fetchContacts } = await import('../../services/contactService')
  vi.mocked(fetchContacts).mockResolvedValue({ items: [contactRow], total: 1, page: 1, pageSize: 20 } as never)
  renderWithProviders(<ContactListPage />)
  // 等到表格真的渲染出这一行，后续的否定断言才有意义 ——
  // 否则"链接不在"可能只是"页面还没加载出来"，那样的负向断言是假绿。
  await screen.findByText('张三')
}

describe('ContactListPage 行内删除的权限收口（086）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('ADMIN 看得见行内「删除」', async () => {
    await renderPage(adminUser)

    expect(queryDelete()).toBeInTheDocument()
  })

  it('无 contact:delete 的 SALES 看不见行内「删除」，但同行的「编辑」仍在', async () => {
    await renderPage(salesNoPerm)

    // 反空洞守卫：该行确实渲染了，且操作列本身跑到了——消失的只有被收口的那个链接
    expect(screen.getByText('张三')).toBeInTheDocument()
    expect(screen.getByText('Acme 科技')).toBeInTheDocument()
    expect(queryEdit()).toBeInTheDocument()
    expect(queryDelete()).not.toBeInTheDocument()
  })

  it('持有 contact:delete 的 SALES 看得见行内「删除」（该权限从此可授予）', async () => {
    await renderPage(salesWithDelete)

    expect(queryDelete()).toBeInTheDocument()
  })
})
