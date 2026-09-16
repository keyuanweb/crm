import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import ApprovalCenterPage from './ApprovalCenterPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'
import type { ApprovalTask } from '../../types/approval'

vi.mock('../../services/approvalService', () => ({
  fetchApprovalTodos: vi.fn(),
  fetchApprovalDone: vi.fn(),
  fetchApprovalDetail: vi.fn(),
  approveTask: vi.fn(),
  rejectTask: vi.fn(),
  transferTask: vi.fn(),
}))
vi.mock('../../services/userService', () => ({
  fetchUsers: vi.fn(async () => ({ items: [], total: 0, page: 1, pageSize: 100 })),
}))

/**
 * 096 · `ApprovalCenterPage` 三个操作链接的**归属判据**测试。
 *
 * <p><b>这不是权限码用例</b>：本页不引入任何权限码（`approval:approve` 是死码，挂码等于
 * 「有码者可审**任何**任务」，比归属判定松 = 削弱）。渲染条件 `canAct()` 逐字镜像后端
 * `ApprovalEngineService.checkApprover`（`approverId` 为 null ⇒ FORBIDDEN；非本人 ⇒ FORBIDDEN，
 * **对 ADMIN 也不通融**）与 `requirePendingTask`（非 `PENDING` ⇒ BAD_REQUEST）。
 *
 * <p>⚠️ **用例必须桩住取数**（本文件正是这么做的）。真端点 `/approvals/todos`、`/approvals/done`
 * 本身就按 `approverId` 过滤（`ApprovalEngineService.todos/done`），所以**跑真后端时，
 * 页面上根本不会出现他人的任务**——只判 status 与判归属走的是同一批数据，破坏打不出红。
 * 桩住取数才有「他人的 PENDING 任务」这种输入，第 2/3/5 例才成立。
 * （这条是 096 调研的一处订正：初稿把「非被分配人看得见必然 403 的按钮」当成当下可观察的缺陷，
 * 实测不成立。见 `specs/096-permission-gating-closeout/spec.md` US3。）
 */
const me: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
const admin: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }

const task = (over: Partial<ApprovalTask>): ApprovalTask => ({
  id: 100,
  instanceId: 5,
  nodeName: '部门经理审批',
  approverType: 'USER',
  status: 'PENDING',
  seq: 1,
  createdAt: '2026-09-16T09:00:00',
  ...over,
})

const approveLink = () => screen.queryByText('pages.approval.list.approve')
const rejectLink = () => screen.queryByText('pages.approval.list.reject')
const transferLink = () => screen.queryByText('pages.approval.list.transfer')
/** 同一 action 列里**不受归属管辖**的兄弟控件：证明该列渲染过了。 */
const detailLink = () => screen.queryByText('pages.approval.list.detail')

async function renderTodos(user: UserInfo, row: ApprovalTask) {
  useAuthStore.setState({ user })
  const { fetchApprovalTodos, fetchApprovalDone } = await import('../../services/approvalService')
  vi.mocked(fetchApprovalTodos).mockResolvedValue([row])
  vi.mocked(fetchApprovalDone).mockResolvedValue([])

  renderWithProviders(<ApprovalCenterPage />)
  // 反空洞守卫：等到这一行渲染出来，否定断言才有意义。
  await screen.findByText(`#${row.instanceId} · ${row.nodeName}`)
}

describe('ApprovalCenterPage 三个操作链接按任务归属渲染（096）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('① 分给我的待办（approverId === 我）：三个操作链接可见', async () => {
    await renderTodos(me, task({ approverId: 2 }))

    expect(approveLink()).toBeInTheDocument()
    expect(rejectLink()).toBeInTheDocument()
    expect(transferLink()).toBeInTheDocument()
  })

  it('② 他人的待办（approverId ≠ 我）：三个都不可见，「详情」照常可见', async () => {
    await renderTodos(me, task({ approverId: 99 }))

    expect(detailLink()).toBeInTheDocument()
    expect(approveLink()).not.toBeInTheDocument()
    expect(rejectLink()).not.toBeInTheDocument()
    expect(transferLink()).not.toBeInTheDocument()
  })

  it('③ approverId 为 null 的待办：三个都不可见（镜像 checkApprover 的 null 分支）', async () => {
    await renderTodos(me, task({ approverId: undefined }))

    expect(detailLink()).toBeInTheDocument()
    expect(approveLink()).not.toBeInTheDocument()
    expect(rejectLink()).not.toBeInTheDocument()
    expect(transferLink()).not.toBeInTheDocument()
  })

  it('④ 我自己的**已办**（status 非 PENDING）：三个都不可见（业务状态闸门仍在）', async () => {
    await renderTodos(me, task({ approverId: 2, status: 'APPROVED' }))

    expect(detailLink()).toBeInTheDocument()
    expect(approveLink()).not.toBeInTheDocument()
    expect(rejectLink()).not.toBeInTheDocument()
    expect(transferLink()).not.toBeInTheDocument()
  })

  it('⑤ ADMIN 看**他人**的待办：仍不可见——本判据对 ADMIN 也不通融', async () => {
    await renderTodos(admin, task({ approverId: 99 }))

    expect(detailLink()).toBeInTheDocument()
    expect(approveLink()).not.toBeInTheDocument()
    expect(rejectLink()).not.toBeInTheDocument()
    expect(transferLink()).not.toBeInTheDocument()
  })
})
