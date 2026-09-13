import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import InvoiceListPage from './InvoiceListPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/invoiceService', () => ({
  fetchInvoices: vi.fn(),
  createInvoice: vi.fn(),
  voidInvoice: vi.fn(),
  fetchInvoiceStats: vi.fn(),
  fetchOrdersForInvoice: vi.fn(),
}))

/**
 * 086 权限收口 · `InvoiceListPage` 行内「作废」的渲染测试。
 *
 * <p>作废发票走 `POST /invoices/{id}/void`，`InvoiceController` 上标的是 `invoice:manage`。
 *
 * <p>本页的判据是**两个条件的合取**：`row.status === 'ISSUED' && can[PERMS.invoiceManage]`
 * （`InvoiceListPage.tsx:158`）。合取式最容易写成假绿——只测"无码看不见"时，
 * "链接不在"完全可能只是因为状态不是 ISSUED 而不是因为没权限。
 * 所以 mock 数据**必须**是 ISSUED 行，且 ③（持码非 ADMIN 可见）这一条不可或缺：
 * 它证明状态那一半确实成立，于是 ② 里消失的唯一变量就只剩权限。
 *
 * <p>行内链接的可见文本是 `pages.invoiceList.status.void`，**与 VOID 行的状态标签同名**，
 * 所以 mock 数据里刻意**不放 VOID 行**（只放 ISSUED），避免断言命中状态标签而假绿——
 * ISSUED 行的状态标签是 `pages.invoiceList.status.issued`，与本断言不冲突。
 *
 * <p>负向用例**必须**用非 ADMIN：`hasPerm` 对 `role === 'ADMIN'` 直通返回 true
 * （`hooks/usePermission.ts:10`），管理员永远测不出「看不见」。
 */
const issuedInvoice = {
  id: 1,
  orderId: 100,
  orderNo: 'SO-2026-001',
  invoiceNo: 'INV-2026-001',
  title: 'Acme 科技首期款',
  amount: 123456,
  invoiceType: 'GENERAL',
  status: 'ISSUED',
  issuedAt: '2026-02-03T10:20:30',
}

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码——"看不见"的对照组必须建立在它之上。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 只多了 `invoice:manage`，其余与基线逐字相同——把差异收敛到唯一变量。 */
const salesWithManage: UserInfo = { ...salesNoPerm, permissions: ['invoice:manage'] }

const withUser = (user: UserInfo) => useAuthStore.setState({ user })

/** 行内「作废」链接。 */
const queryVoid = () => screen.queryByText('pages.invoiceList.status.void')

async function renderPage(user: UserInfo) {
  withUser(user)
  const { fetchInvoices, fetchInvoiceStats } = await import('../../services/invoiceService')
  vi.mocked(fetchInvoices).mockResolvedValue({ items: [issuedInvoice], total: 1, page: 1, pageSize: 10 } as never)
  vi.mocked(fetchInvoiceStats).mockResolvedValue({
    totalInvoiceAmount: 123456,
    totalOrderAmount: 200000,
    invoiceRate: 61.7,
    byOrder: [],
  } as never)
  renderWithProviders(<InvoiceListPage />)
  // 等到表格真的渲染出发票号，后续的否定断言才有意义 ——
  // 否则"链接不在"可能只是"页面还没加载出来"，那样的负向断言是假绿。
  await screen.findByText('INV-2026-001')
}

describe('InvoiceListPage 行内作废的权限收口（086）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('ADMIN 看得见 ISSUED 行的「作废」', async () => {
    await renderPage(adminUser)

    expect(queryVoid()).toBeInTheDocument()
  })

  it('无 invoice:manage 的 SALES 看不见「作废」（同一条 ISSUED 行仍在屏上）', async () => {
    await renderPage(salesNoPerm)

    // 反空洞守卫：先证明这条 ISSUED 行确实渲染了、状态确实是 ISSUED ——
    // 否则"链接不在"可能只是状态没进 ISSUED，与权限无关
    expect(screen.getByText('INV-2026-001')).toBeInTheDocument()
    expect(screen.getByText('pages.invoiceList.status.issued')).toBeInTheDocument()
    expect(queryVoid()).not.toBeInTheDocument()
  })

  it('持有 invoice:manage 的 SALES 看得见「作废」（该权限从此可授予）', async () => {
    await renderPage(salesWithManage)

    // ③ 同时证明合取式的两半都成立：状态是 ISSUED，码也真的能授予
    expect(queryVoid()).toBeInTheDocument()
  })
})
