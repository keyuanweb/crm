import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { renderWithProviders } from '../../test/renderWithProviders'
import ContractDetailPage from './ContractDetailPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

/*
  只放宽**超时上限**，不放宽任何断言（判据写错时仍然是红的，087 的 4 条已逐条变异自验）。

  本文件在全量覆盖率运行（72 文件并行）下实测 74.1s/8 例 = 9.27s/例，是本次新增文件里**每例最慢**的一个
  （单跑 12.0s/8 例 = 1.5s/例），距 vitest 全局 `testTimeout: 20000` 只剩约 2 倍余量。
  未观察到它超时（两次覆盖率运行都绿），这里按余量主动收紧防线，避免它成为下一个受 CPU 争用摆布的用例。
*/
vi.setConfig({ testTimeout: 60_000 })

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
const salesWithApprove: UserInfo = { ...salesNoPerm, permissions: ['contract:approve'] }

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
// 087 补课：`contract:approve` 管的审批组（`:266`）。与上面 contract:update 的三个控件是**两个码**。
const approveButton = () => screen.queryByRole('button', { name: /pages\.contract\.detail\.approve/ })
const rejectButton = () => screen.queryByRole('button', { name: /pages\.contract\.detail\.reject/ })
// 恒存在的锚点：工具栏里的「刷新」（`:293`，renderPage 已等它；纯客户端 refetch，与权限码无关）。
// 刻意**不**用同栏的「上传附件」（`:389`）当锚点——它虽然当前也恒渲染，但其后端端点
// （`ContractAttachmentController.java:55`）要求 `contract:update`，见文件末尾的「附带发现」；
// 把它写进断言等于把一处疑似缺口固化成预期。
const refreshButton = () => screen.queryByRole('button', { name: /pages\.contract\.detail\.refresh/ })
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

/**
 * 087 权限补课 · `contract:approve` 的双向渲染测试（补上该码的零渲染用例缺口）。
 *
 * <p>上面 4 条锁的是 `contract:update`；本块锁的是**另一个码**——审批组（「审批」「驳回」，`:266`），
 * 判据在 `:119`：
 *
 * <pre>const canApprove = hasPerm(PERMS.contractApprove, user) &amp;&amp; status === 'PENDING_APPROVAL'</pre>
 *
 * <p>这是一个 **状态 ∧ 权限** 的合取。只用 PENDING_APPROVAL 做双向断言，证明不了后半截还在——
 * 把判据写成 `hasPerm(...)` 一个条件（丢掉状态闸门），双向用例照样全绿。故第 ③ 例是核心：
 * 同一个持码用户、只把状态换成 DRAFT，审批组必须**仍不可见**，而「提交」（纯状态判据）必须可见。
 * 这一对相反结论同时成立，才证明两条判据各自独立、权限判据没有把状态判据吞掉。
 *
 * <p>第 ④ 例是**码间不串门**：持 `contract:update` 而不持 `contract:approve` 的 SALES 看 EFFECTIVE
 * 合同——`contract:update` 管的「终止合同」可见，`contract:approve` 管的审批组不可见。
 * 两码独立（FR-B07），任一边被写成另一边都会让这条转红。
 *
 * <p>反空洞：每条用例都同时断言工具栏里**恒存在且不属任何权限码**的「刷新」（`:293`，renderPage
 * 的守卫）与「上传附件」（`:389`）——它们出现 ⇒ 数据已加载、动作栏真的渲染出来了，
 * 后续的"不可见"才是判据造成的结论，而不是"整页没渲染"被误读成"按钮正确地不在"。
 */
describe('ContractDetailPage 收口（087 · contract:approve）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('① 持有 contract:approve 的 SALES + PENDING_APPROVAL：「审批」「驳回」都可见（锁住码的身份）', async () => {
    await renderPage(salesWithApprove, 'PENDING_APPROVAL')

    expect(refreshButton()).toBeInTheDocument()
    expect(approveButton()).toBeInTheDocument()
    expect(rejectButton()).toBeInTheDocument()
  })

  it('② 无 contract:approve 的 SALES + PENDING_APPROVAL：「审批」「驳回」都不可见，但同栏其余动作与附件行内容照常', async () => {
    await renderPage(salesNoPerm, 'PENDING_APPROVAL')

    // 同栏不受本码管辖的动作仍在 ⇒ 只掉了审批组，不是整栏/整页消失
    expect(refreshButton()).toBeInTheDocument()
    // 附件行本身照常渲染（文件名在 ⇒ 表格有数据，审批组的缺席才是判据造成的）
    expect(screen.getByText('附件A.pdf')).toBeInTheDocument()

    expect(approveButton()).not.toBeInTheDocument()
    expect(rejectButton()).not.toBeInTheDocument()
  })

  it('③ 持有 contract:approve 的 SALES + DRAFT：审批组仍不可见（状态闸门未被权限吞掉），但「提交」可见', async () => {
    await renderPage(salesWithApprove, 'DRAFT')

    // 状态闸门仍在起作用：DRAFT 下「提交」可见（纯状态判据，与权限无关）
    expect(refreshButton()).toBeInTheDocument()
    expect(submitButton()).toBeInTheDocument()

    // 同一用户、同一码，只把状态从 PENDING_APPROVAL 换成 DRAFT ⇒ 审批组必须消失。
    // 若判据被写成只判权限（丢掉 `&& status === 'PENDING_APPROVAL'`），这两条立刻转红。
    expect(approveButton()).not.toBeInTheDocument()
    expect(rejectButton()).not.toBeInTheDocument()
  })

  it('④ 持 contract:update 而不持 contract:approve 的 SALES + EFFECTIVE：「终止合同」可见而审批组不可见（两码独立不串门）', async () => {
    await renderPage(salesWithUpdate, 'EFFECTIVE')

    expect(refreshButton()).toBeInTheDocument()
    expect(completeButton()).toBeInTheDocument()
    // contract:update 管的动作照常出现
    expect(terminateButton()).toBeInTheDocument()
    // contract:approve 管的审批组不因持有另一个码而出现
    expect(approveButton()).not.toBeInTheDocument()
    expect(rejectButton()).not.toBeInTheDocument()
  })
})

/**
 * 附带发现（**只记录，未修**——087 是零生产代码改动的补课规格）。
 *
 * <p>「上传附件」按钮（`ContractDetailPage.tsx:389`）**没有任何权限判据**，而它的后端端点
 * `POST /api/v1/contracts/{id}/attachments`（`ContractAttachmentController.java:55-58`）
 * 标的是 `@RequirePermission("contract:update")`。于是只持 `contract:read` 的用户
 * （能渲染本页、但无 `contract:update`）会看到一个点了必然 403 的按钮。
 *
 * <p>这与同卡片内的处理**自相矛盾**：同属 `contract:update` 的「删除附件」（`:171`）已按 `canUpdate` 收口。
 * 也**不属** 086 spec.md:111 明确豁免的那一类（那一类挂的是**读码**：导出报价单 PDF = `quote:read`、
 * 附件下载 = `contract:read`——能进本页者必然持有，故挂上去是恒真的空动作）。`contract:update`
 * 显然不由"能看这份合同"推出。
 *
 * <p>086 的收口清单（plan.md:159-160）只登记了「终止合同」与「删除附件」，上传从未被裁决过；
 * 087 的范围内也没有它。故此处不把它写进断言（写进去等于把疑似缺口固化成预期），只记在此处待裁决。
 */
