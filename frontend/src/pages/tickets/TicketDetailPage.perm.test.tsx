import { describe, expect, it, vi, beforeEach } from 'vitest'
import { Route, Routes } from 'react-router-dom'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import TicketDetailPage from './TicketDetailPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'
import type { TicketStatus } from '../../types/ticket'

vi.mock('../../services/ticketService', () => ({
  fetchTicket: vi.fn(),
  fetchTicketReplies: vi.fn(),
  assignTicket: vi.fn(),
  replyTicket: vi.fn(),
  transitionTicket: vi.fn(),
  deleteTicket: vi.fn(),
  createTicket: vi.fn(),
  updateTicket: vi.fn(),
  fetchTickets: vi.fn(),
}))
vi.mock('../../services/userService', () => ({
  fetchUsers: vi.fn(async () => ({ items: [], total: 0, page: 1, pageSize: 100 })),
  createUser: vi.fn(),
  updateUser: vi.fn(),
  resetPassword: vi.fn(),
}))
// 满意度区块（SurveyBlock，051）在挂载时就发请求；它的 service 若不 mock 就会走未打桩的
// 网络层（测试里挂起不返回），本文件与它无关，直接打桩让请求有确定结果。
vi.mock('../../services/ticketSurveyService', () => ({
  fetchTicketSurvey: vi.fn(async () => null),
  submitTicketSurvey: vi.fn(),
}))

/**
 * 086 权限收口 · `TicketDetailPage` 的**四码互不串码**测试。
 *
 * <p>本页是全批唯一「一页挂四个独立码」的页面（`TicketController.java:110-135`），
 * 四个动作打四个端点、四个码，所以它是本批**互不串码**断言价值最高的一处：
 *
 * <pre>
 *   删除工单      DELETE /tickets/{id}            → ticket:delete
 *   分配处理人    POST   /tickets/{id}/assign      → ticket:assign
 *   状态流转三连  POST   /tickets/{id}/transition  → ticket:update（同一端点、同一码，整组）
 *   发送回复      POST   /tickets/{id}/reply       → ticket:reply
 * </pre>
 *
 * <p>「三个按钮同一个码」指的是「开始处理 / 标记已解决 / 关闭工单」——它们打的是同一个
 * `transition` 端点，所以界面上只能有一个判据 `ticket:update`。三者按工单状态**三选一**出现：
 * OPEN → 开始处理、IN_PROGRESS → 标记已解决、RESOLVED → 关闭工单（`TicketDetailPage.tsx:176-190`）。
 * 断言必须按状态分别取值，否则「按钮不在」可能只是状态不对，而不是权限收口生效。
 *
 * <p>多码页的核心断言是**互不串码**：只持 `ticket:reply` 的用户必须看得见回复、看不见分配
 * （反方向同理）。只有这一条能把「这个按钮由这个码管」与「这个按钮恰好没渲染」区分开——
 * 单码页的三条断言做不到这件事，因为那里所有控件同生同灭。
 *
 * <p>负向用例**必须**用非 ADMIN 用户：`hasPerm` 对 `role === 'ADMIN'` 直接短路返回 true
 * （`hooks/usePermission.ts:10`），用管理员永远拿不到「看不见」的结论。
 */

const TICKET_TITLE = '打印机无法连接'

const makeTicket = (status: TicketStatus) => ({
  id: 1,
  customerId: 10,
  customerName: 'Acme 科技',
  title: TICKET_TITLE,
  description: '三楼打印机连不上网',
  priority: 'HIGH',
  status,
  assigneeId: 5,
  assigneeName: '张三',
  slaStatus: 'NORMAL',
  replyCount: 0,
  version: 0,
  createdAt: '2026-09-01T10:00:00',
})

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }

/** 非 ADMIN 基线：零权限码。负向用例必须建立在它之上（ADMIN 在 hasPerm 里直通）。 */
const supportNoPerm: UserInfo = { id: 2, username: 'support01', displayName: '客服一', role: 'SUPPORT', permissions: [] }

/** 只多一个码，其余与基线逐字相同——把差异收敛到唯一变量，这就是「互不串码」的取法。 */
const supportReplyOnly: UserInfo = { ...supportNoPerm, permissions: ['ticket:reply'] }
const supportAssignOnly: UserInfo = { ...supportNoPerm, permissions: ['ticket:assign'] }
const supportUpdateOnly: UserInfo = { ...supportNoPerm, permissions: ['ticket:update'] }
const supportAllPerms: UserInfo = {
  ...supportNoPerm,
  permissions: ['ticket:delete', 'ticket:assign', 'ticket:update', 'ticket:reply'],
}

const btnDelete = () => screen.queryByRole('button', { name: /pages\.ticket\.detail\.deleteTicket/ })
const btnAssign = () => screen.queryByRole('button', { name: /pages\.ticket\.detail\.assignAssignee/ })
const btnStart = () => screen.queryByRole('button', { name: /pages\.ticket\.detail\.startProcess/ })
const btnResolve = () => screen.queryByRole('button', { name: /pages\.ticket\.detail\.markResolved/ })
const btnClose = () => screen.queryByRole('button', { name: /pages\.ticket\.detail\.closeTicket/ })
const btnReply = () => screen.queryByRole('button', { name: /pages\.ticket\.detail\.sendReply/ })
/** 回复块（`TicketDetailPage.tsx:280-297`）整块同判据：输入框与发送按钮必须同生同灭。 */
const replyBox = () => screen.queryByPlaceholderText('pages.ticket.detail.replyPlaceholder')

async function renderPage(user: UserInfo, status: TicketStatus = 'OPEN') {
  useAuthStore.setState({ user })
  const { fetchTicket, fetchTicketReplies } = await import('../../services/ticketService')
  vi.mocked(fetchTicket).mockResolvedValue(makeTicket(status) as never)
  vi.mocked(fetchTicketReplies).mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 100 } as never)

  renderWithProviders(
    <Routes>
      <Route path="/tickets/:id" element={<TicketDetailPage />} />
    </Routes>,
    { route: '/tickets/1' },
  )
  // 等到工单标题真的渲染出来，后续的否定断言才有意义 ——
  // 否则「按钮不在」可能只是「页面还没加载完」，那样的负向断言是假绿。
  await screen.findByText(TICKET_TITLE, {}, { timeout: 5000 })
}

describe('TicketDetailPage 四码收口（086）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('① ADMIN：删除 / 分配 / 开始处理 / 回复 全都看得见（收口未误伤管理员）', async () => {
    await renderPage(adminUser, 'OPEN')

    expect(btnDelete()).toBeInTheDocument()
    expect(btnAssign()).toBeInTheDocument()
    expect(btnStart()).toBeInTheDocument()
    expect(btnReply()).toBeInTheDocument()
    expect(replyBox()).toBeInTheDocument()
  })

  it('② 零权限的 SUPPORT：四个动作全不可见（先确认页面已渲染）', async () => {
    await renderPage(supportNoPerm, 'OPEN')

    // 反空洞守卫：页面确实渲染出了工单内容，否定断言才不是假绿
    expect(screen.getByText(TICKET_TITLE)).toBeInTheDocument()
    expect(screen.getByText('pages.ticket.detail.ticketInfo')).toBeInTheDocument()

    expect(btnDelete()).not.toBeInTheDocument()
    expect(btnAssign()).not.toBeInTheDocument()
    expect(btnStart()).not.toBeInTheDocument()
    expect(btnReply()).not.toBeInTheDocument()
    expect(replyBox()).not.toBeInTheDocument()
  })

  it('③ 持全部四码的 SUPPORT：四个动作全可见（这四码从此可授予）', async () => {
    await renderPage(supportAllPerms, 'OPEN')

    expect(btnDelete()).toBeInTheDocument()
    expect(btnAssign()).toBeInTheDocument()
    expect(btnStart()).toBeInTheDocument()
    expect(btnReply()).toBeInTheDocument()
    expect(replyBox()).toBeInTheDocument()
  })

  it('④ 互不串码：只持 ticket:reply ⇒ 只有回复可见，分配 / 删除 / 开始处理不可见', async () => {
    await renderPage(supportReplyOnly, 'OPEN')

    expect(screen.getByText(TICKET_TITLE)).toBeInTheDocument() // 反空洞守卫
    expect(btnReply()).toBeInTheDocument()
    expect(replyBox()).toBeInTheDocument()

    // 本批的核心价值：同页的其他码没有被这一码顺带放行
    expect(btnAssign()).not.toBeInTheDocument()
    expect(btnDelete()).not.toBeInTheDocument()
    expect(btnStart()).not.toBeInTheDocument()
  })

  it('⑤ 互不串码（反向）：只持 ticket:assign ⇒ 只有分配可见，回复不可见', async () => {
    await renderPage(supportAssignOnly, 'OPEN')

    expect(screen.getByText(TICKET_TITLE)).toBeInTheDocument() // 反空洞守卫
    expect(btnAssign()).toBeInTheDocument()

    expect(btnReply()).not.toBeInTheDocument()
    expect(replyBox()).not.toBeInTheDocument()
    expect(btnDelete()).not.toBeInTheDocument()
    expect(btnStart()).not.toBeInTheDocument()
  })

  it('⑥ ticket:update 组（IN_PROGRESS）：持码 ⇒ 标记已解决可见，开始处理 / 关闭工单不可见（状态三选一）', async () => {
    await renderPage(supportUpdateOnly, 'IN_PROGRESS')

    expect(screen.getByText(TICKET_TITLE)).toBeInTheDocument() // 反空洞守卫
    expect(btnResolve()).toBeInTheDocument()
    // 同组另外两个按钮按状态取值，此时不该出现——这条把「状态判断」与「权限判断」分开
    expect(btnStart()).not.toBeInTheDocument()
    expect(btnClose()).not.toBeInTheDocument()
  })

  it('⑦ ticket:update 组（IN_PROGRESS）：零权限的 SUPPORT ⇒ 标记已解决不可见', async () => {
    await renderPage(supportNoPerm, 'IN_PROGRESS')

    expect(screen.getByText(TICKET_TITLE)).toBeInTheDocument() // 反空洞守卫
    expect(btnResolve()).not.toBeInTheDocument()
  })

  it('⑧ ticket:update 组（RESOLVED）：持码 ⇒ 关闭工单可见', async () => {
    await renderPage(supportUpdateOnly, 'RESOLVED')

    expect(screen.getByText(TICKET_TITLE)).toBeInTheDocument() // 反空洞守卫
    expect(btnClose()).toBeInTheDocument()
  })
})
