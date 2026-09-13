import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { renderWithProviders } from '../../test/renderWithProviders'
import QuoteDetailPage from './QuoteDetailPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/quoteService', () => ({
  fetchQuote: vi.fn(),
  submitQuote: vi.fn(),
  approveQuote: vi.fn(),
  rejectQuote: vi.fn(),
  exportQuotePdf: vi.fn(),
}))
vi.mock('../../services/signatureService', () => ({
  signQuote: vi.fn(),
  fetchQuoteSignature: vi.fn(),
}))
// 签署区与本次收口无关（它走 signatureService，且自有 canSign 业务判据），替身化以免噪声查询。
vi.mock('../../components/SignSection', () => ({ default: () => null }))
// 状态标签走 labelOf(t, ENUM_KEYS.quoteStatus, code)；替身化以免依赖真实枚举资源。
vi.mock('../../constants/enumLabels', () => ({
  ENUM_KEYS: { quoteStatus: { DRAFT: 'common.status.draft' } },
  labelOf: (_t: unknown, _k: unknown, code: string) => code,
}))

/**
 * 087 权限补课 · `QuoteDetailPage` 的双向渲染测试（补上 `quote:approve` 的零用例缺口）。
 *
 * <p>本页只有一处被权限码管辖的动作——审批组（「审批」「驳回」，`QuoteDetailPage.tsx:148`），
 * 判据在 `:100`：
 *
 * <pre>const canApprove = hasPerm(PERMS.quoteApprove, user) &amp;&amp; status === 'PENDING_APPROVAL'</pre>
 *
 * <p>这是一个 **状态 ∧ 权限** 的合取。只用 PENDING_APPROVAL 一种状态做双向断言，
 * 证明不了后半截（`status`）还在——把判据写成 `hasPerm(...)` 一个条件（丢掉状态闸门），
 * 双向用例照样全绿：它对**任何人**都只在 PENDING_APPROVAL 下多出一个按钮。故第 ④ 例是核心：
 * 同一个持码用户、只把状态换成 DRAFT，审批组必须**仍不可见**，而「提交审批」（纯状态判据）必须可见——
 * 这一对相反结论同时成立，才证明两条判据各自独立、权限判据没有把状态判据吞掉。
 *
 * <p>不可收口的对照项：「导出报价单 PDF」（`:164`）刻意不挂权限码——该端点
 * （`QuoteController.java:128-131`）挂的是**读码** `quote:read`，能渲染出本页的人必然已持有它，
 * 挂上去是恒真的空动作。第 ③ 例顺带把这条豁免锁住。
 *
 * <p>反空洞：每条用例都同时断言「导出 PDF」按钮（`:164`，**始终**渲染且不属于任何权限码）——
 * 它出现 ⇒ 数据已加载、工具栏真的渲染出来了，后续的"不可见"才是判据造成的结论，
 * 而不是"整页没渲染出来"被误读成"按钮正确地不在"。
 */
const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
const salesWithApprove: UserInfo = { ...salesNoPerm, permissions: ['quote:approve'] }

const makeQuote = (status: string) => ({
  id: 1,
  quoteNo: 'BJ-2026-001',
  customerId: 9,
  customerName: 'Acme 科技',
  status,
  totalAmount: 123400,
  version: 1,
  createdAt: '2026-01-01T10:00:00',
  validUntil: '2026-03-01',
  items: [{ id: 1, productName: 'CRM 标准版', unitPrice: 123400, quantity: 1, discount: 1, lineTotal: 123400 }],
})

const approveButton = () => screen.queryByRole('button', { name: /pages\.quote\.detail\.approve/ })
const rejectButton = () => screen.queryByRole('button', { name: /pages\.quote\.detail\.reject/ })
const submitButton = () => screen.queryByRole('button', { name: /pages\.quote\.detail\.submit/ })
// 恒存在的锚点：不属任何权限码、任何状态下都渲染。
const exportPdfButton = () => screen.queryByRole('button', { name: /pages\.quote\.detail\.exportPdf/ })

async function renderPage(user: UserInfo, status: string) {
  const { fetchQuote } = await import('../../services/quoteService')
  vi.mocked(fetchQuote).mockResolvedValue(makeQuote(status) as never)
  useAuthStore.setState({ user })

  // useParams 取的是路由参数：**仅**给 MemoryRouter 的 initialEntries 不提供 params，
  // 必须经 <Routes><Route path="/quotes/:id"> 才拿得到（086 踩过）。
  renderWithProviders(
    <Routes>
      <Route path="/quotes/:id" element={<QuoteDetailPage />} />
    </Routes>,
    { route: '/quotes/1' },
  )

  await screen.findByRole('button', { name: /pages\.quote\.detail\.exportPdf/ }, { timeout: 5000 })
}

describe('QuoteDetailPage 收口（087 · quote:approve）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('① ADMIN + PENDING_APPROVAL：「审批」「驳回」都可见（ADMIN 在 hasPerm 里直通）', async () => {
    await renderPage(adminUser, 'PENDING_APPROVAL')

    expect(exportPdfButton()).toBeInTheDocument()
    expect(approveButton()).toBeInTheDocument()
    expect(rejectButton()).toBeInTheDocument()
  })

  it('② 持有 quote:approve 的 SALES + PENDING_APPROVAL：「审批」「驳回」都可见（锁住码的身份）', async () => {
    await renderPage(salesWithApprove, 'PENDING_APPROVAL')

    expect(exportPdfButton()).toBeInTheDocument()
    expect(approveButton()).toBeInTheDocument()
    expect(rejectButton()).toBeInTheDocument()
  })

  it('③ 无 quote:approve 的 SALES + PENDING_APPROVAL：「审批」「驳回」都不可见，但同栏其余动作与明细照常', async () => {
    await renderPage(salesNoPerm, 'PENDING_APPROVAL')

    // 同栏不受本码管辖的动作仍在 ⇒ 只掉了审批组，不是整栏/整页消失。
    // 导出 PDF 走 quote:read ⇒ 刻意不收口，无码者照样看得到（这是设计豁免，不是漏接线）。
    expect(exportPdfButton()).toBeInTheDocument()
    // 产品明细行本身照常渲染（明细在 ⇒ 表格有数据，审批组的缺席才是判据造成的）
    expect(screen.getByText('CRM 标准版')).toBeInTheDocument()
    // PENDING_APPROVAL 下本来就不能再提交（纯状态判据，与本码无关）
    expect(submitButton()).not.toBeInTheDocument()

    expect(approveButton()).not.toBeInTheDocument()
    expect(rejectButton()).not.toBeInTheDocument()
  })

  it('④ 持有 quote:approve 的 SALES + DRAFT：审批组仍不可见（状态闸门未被权限吞掉），但「提交审批」可见', async () => {
    await renderPage(salesWithApprove, 'DRAFT')

    // 状态闸门仍在起作用：DRAFT 下「提交审批」可见（纯状态判据，与权限无关）
    expect(exportPdfButton()).toBeInTheDocument()
    expect(submitButton()).toBeInTheDocument()

    // 同一用户、同一码，只把状态从 PENDING_APPROVAL 换成 DRAFT ⇒ 审批组必须消失。
    // 若判据被写成只判权限（丢掉 `&& status === 'PENDING_APPROVAL'`），这两条立刻转红。
    expect(approveButton()).not.toBeInTheDocument()
    expect(rejectButton()).not.toBeInTheDocument()
  })
})
