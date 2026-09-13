import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { renderWithProviders } from '../../test/renderWithProviders'
import ContractDetailPage from './ContractDetailPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/contractService', () => ({
  fetchContract: vi.fn(),
  submitContract: vi.fn(),
  approveContract: vi.fn(),
  rejectContract: vi.fn(),
  effectiveContract: vi.fn(),
  completeContract: vi.fn(),
  terminateContract: vi.fn(),
  uploadContractAttachment: vi.fn(),
  downloadContractAttachment: vi.fn(),
  deleteContractAttachment: vi.fn(),
}))
vi.mock('../../services/signatureService', () => ({
  signContract: vi.fn(),
  fetchContractSignature: vi.fn(),
}))
// 签署区与本次收口无关（它走 signatureService，且自有 canSign 业务判据），替身化以免噪声查询。
vi.mock('../../components/SignSection', () => ({ default: () => null }))
// 状态标签走 labelOf(t, ENUM_KEYS.contractStatus, code)；替身化以免依赖真实枚举资源。
vi.mock('../../constants/enumLabels', () => ({
  ENUM_KEYS: { contractStatus: { EFFECTIVE: 'effective', DRAFT: 'draft' } },
  labelOf: (_t: unknown, _k: unknown, code: string) => code,
}))

/**
 * 086 权限收口 · `ContractDetailPage` 的双向渲染测试（补齐 T081 指出的缺口）。
 *
 * <p>本页有两个被收口的动作，挂的都是 `contract:update`（`ContractController.java:138-144`
 * 的 terminate 与 `ContractAttachmentController.java:82-86` 的 delete attachment），
 * 但两者的**闸门形态不同**，这正是本文件要锁住的：
 *
 * <ul>
 *   <li>「终止合同」（`:286`）嵌在 `canFinish`（状态 ∈ APPROVED/SIGNED/EFFECTIVE）**之内** ⇒ 是
 *       `状态 ∧ 权限` 两道闸门，权限判据**没有**替代状态判据；
 *   <li>「删除附件」（`:171`）只有 `canUpdate ? … : null`，**不**看状态 ⇒ 草稿合同也能删附件。
 * </ul>
 *
 * <p>第 4 例是核心：持有 `contract:update` 的 SALES 看 DRAFT 合同——「终止合同」必须**不可见**、
 * 而「删除附件」必须**可见**。这一对相反结论同时成立，才证明两条判据是各自独立的 ∧，
 * 而不是把状态判据一起收掉或把权限判据写成了恒真。
 *
 * <p>不可收口的对照项：附件「下载」（`:165`）走 `contract:read`
 * （`ContractAttachmentController.java:31`）——能渲染出本页的人必然已持有该读码，
 * 挂上去是**恒真的空动作**，故刻意不挂。第 2 例顺带把这条豁免锁住。
 */
const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
const salesWithUpdate: UserInfo = { ...salesNoPerm, permissions: ['contract:update'] }

const makeContract = (status: string) => ({
  id: 1,
  contractNo: 'HT-2026-001',
  title: '标准销售合同',
  status,
  amount: 1234500,
  customerId: 9,
  customerName: 'Acme 科技',
  quoteId: null,
  startDate: '2026-01-01',
  endDate: '2026-12-31',
  approvedAt: null,
  effectiveAt: null,
  remark: null,
  content: null,
  createdAt: '2026-01-01T10:00:00',
  attachments: [{ id: 5, fileName: '附件A.pdf', fileSize: 2048, createdAt: '2026-01-02T09:00:00' }],
})

const terminateButton = () => screen.queryByRole('button', { name: /pages\.contract\.detail\.terminate/ })
const completeButton = () => screen.queryByRole('button', { name: /pages\.contract\.detail\.markComplete/ })
const submitButton = () => screen.queryByRole('button', { name: /pages\.contract\.detail\.submit/ })
const deleteAttachmentLink = () => screen.queryByText('pages.contract.detail.delete')
// 只匹配下载链接的文案；**不要**把附件文件名并进这个正则——那样会同时命中表格里的文件名单元格，
// queryByText 对多命中会直接抛错（而不是返回第一条）。
const downloadAttachmentLink = () => screen.queryByText('pages.contract.detail.download')

async function renderPage(user: UserInfo, status: string) {
  const { fetchContract } = await import('../../services/contractService')
  vi.mocked(fetchContract).mockResolvedValue(makeContract(status) as never)
  useAuthStore.setState({ user })

  renderWithProviders(
    <Routes>
      <Route path="/contracts/:id" element={<ContractDetailPage />} />
    </Routes>,
    { route: '/contracts/1' },
  )

  // 反空洞守卫：「刷新」按钮（`:293`）**始终**渲染且不受任何权限管辖。
  // 它出现 ⇒ 数据已加载、页面真的渲染出来了，后续的"不可见"才是真结论而非"整页没渲染"。
  await screen.findByRole('button', { name: /pages\.contract\.detail\.refresh/ }, { timeout: 5000 })
}

describe('ContractDetailPage 收口（086）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('① ADMIN + EFFECTIVE：「终止合同」「删除附件」都可见', async () => {
    await renderPage(adminUser, 'EFFECTIVE')

    expect(terminateButton()).toBeInTheDocument()
    expect(deleteAttachmentLink()).toBeInTheDocument()
  })

  it('② 无 contract:update 的 SALES：「终止合同」「删除附件」都不可见，但同栏其余动作与附件行内容照常', async () => {
    await renderPage(salesNoPerm, 'EFFECTIVE')

    // 同栏不受本码管辖的动作仍在 ⇒ 只掉了这两个，不是整栏消失
    expect(completeButton()).toBeInTheDocument()
    // 附件行本身照常渲染（文件名在 ⇒ 表格有数据，删除链接的缺席才是判据造成的）
    expect(screen.getByText('附件A.pdf')).toBeInTheDocument()
    // 下载走 contract:read ⇒ 刻意不收口，无码者照样看得到（这是设计豁免，不是漏接线）
    expect(downloadAttachmentLink()).toBeInTheDocument()

    expect(terminateButton()).not.toBeInTheDocument()
    expect(deleteAttachmentLink()).not.toBeInTheDocument()
  })

  it('③ 持有 contract:update 的 SALES：「终止合同」「删除附件」都可见（锁住码的身份）', async () => {
    await renderPage(salesWithUpdate, 'EFFECTIVE')

    expect(terminateButton()).toBeInTheDocument()
    expect(deleteAttachmentLink()).toBeInTheDocument()
  })

  it('④ 持有 contract:update 的 SALES + DRAFT：附件删除可见（不看状态），但终止合同不可见（状态闸门未被权限吞掉）', async () => {
    await renderPage(salesWithUpdate, 'DRAFT')

    // 状态闸门仍在起作用：DRAFT 下「标记完成」不可见、「提交」可见（两者都与权限无关）
    expect(submitButton()).toBeInTheDocument()
    expect(completeButton()).not.toBeInTheDocument()

    // 同一用户、同一码：删除附件可见（无状态判据），终止合同不可见（有状态判据）——这一对相反结论
    // 同时成立，才证明两条判据各自独立，权限判据没有替代状态判据。
    expect(deleteAttachmentLink()).toBeInTheDocument()
    expect(terminateButton()).not.toBeInTheDocument()
  })
})
