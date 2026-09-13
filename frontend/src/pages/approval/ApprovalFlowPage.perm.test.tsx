import { describe, expect, it, vi, beforeEach } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import ApprovalFlowPage from './ApprovalFlowPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'
import type { ApprovalFlow } from '../../types/approval'

vi.mock('../../services/approvalService', () => ({
  createApprovalFlow: vi.fn(),
  deleteApprovalFlow: vi.fn(),
  fetchApprovalFlows: vi.fn(),
  updateApprovalFlow: vi.fn(),
}))

/**
 * 086 权限收口 · `ApprovalFlowPage`「删除审批流」的双向渲染测试。
 *
 * <p><b>控件 → 判据 → 码</b>：行内动作列的第二格（`Popconfirm` 包着的红色「删除」链接）由
 * `can[PERMS.workflowManage]` 决定是否渲染，`PERMS.workflowManage === 'workflow:manage'`
 * （`constants/permissions.ts:109`）。后端对得上：`ApprovalController.java:66-67` 的
 * `@DeleteMapping("/approval-flows/{id}")` 挂的正是 `workflow:manage`；
 * service 侧 `deleteApprovalFlow` → `DELETE /approval-flows/{id}`（`services/approvalService.ts:34-36`）。
 *
 * <p><b>同码不同义的陷阱（第 4 例专门锁它）</b>：`workflow:delete` 是**另一个**真实存在的码
 * （`WorkflowController` 删工作流规则用），与 `workflow:manage` 名字只差一个词。本页只认
 * `workflow:manage`——持 `workflow:delete` 者在这里必须仍看不见「删除审批流」。这一条是"码挂错"
 * 这类静默故障唯一能被抓到的形态（挂错的码如果恰好也被授给同一批人，正负向都会全绿）。
 *
 * <p><b>如实说明</b>：`GET /approval-flows`（`ApprovalController.java:43-44`）挂的**也是**
 * `workflow:manage`，所以生产环境里非持有者连这张表都拉不出来（403），
 * 真实数据行不可能出现。本文件把 `fetchApprovalFlows` 替身化，于是能在"无码"下渲染出数据行，
 * 借此把「删除不见了」与「页面没渲染」彻底分开——第 2 例的反空洞守卫就是这个用途。
 * 换句话说：第 2 例锁的是**判据的正确性**，不是生产里的可观测差异。
 *
 * <p><b>刻意不在此文件断言的两处</b>：表单弹窗内的「节点删除」按钮与只读 `Switch`
 * （`useCondition` 开关、`colEnabled` 的启用指示）都是纯本地 state 的编辑态控件，**086 刻意未收口**。
 * 既不断言它们可见、也不断言它们不可见——弹窗默认关闭，且它们与权限码无关，断言只会误导。
 *
 * <p>负向用例**必须**用非 ADMIN：`hasPerm` 对 `role === 'ADMIN'` 直通返回 true
 * （`hooks/usePermission.ts:10`），管理员永远测不出「看不见」。
 */
const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码——"看不见"的对照组必须建立在它之上。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 只多了 `workflow:manage`，其余与基线逐字相同——把差异收敛到唯一变量。 */
const salesWithManage: UserInfo = { ...salesNoPerm, permissions: ['workflow:manage'] }

/** 两行数据：普通流 + 带金额条件分支的流（后者多渲染一个橙色 Tag，是额外的"真实内容"。 */
const plainFlow: ApprovalFlow = {
  id: 3,
  name: '合同审批流',
  businessType: 'CONTRACT',
  nodes: JSON.stringify([{ name: '经理审批', approverType: 'ROLE', approverValue: 'SALES_MANAGER' }]),
  enabled: true,
}
const conditionalFlow: ApprovalFlow = {
  id: 4,
  name: '报价单审批流',
  businessType: 'QUOTE',
  nodes: JSON.stringify([
    { name: '总监审批', approverType: 'ROLE' },
    { name: '财务复核', approverType: 'USER' },
  ]),
  conditionJson: JSON.stringify({
    field: 'amount',
    op: 'GT',
    value: 100000,
    extraNodes: [{ name: '大额加签', approverType: 'MANAGER' }],
  }),
  enabled: false,
}

/** 两行各有一个「删除」/「编辑」，一律用 queryAllByText 避免 getByText 的多处命中抛错。 */
const queryDeletes = () => screen.queryAllByText('pages.approvalFlow.delete')
const queryEdits = () => screen.queryAllByText('pages.approvalFlow.edit')

async function renderPage(user: UserInfo) {
  useAuthStore.setState({ user })
  const { fetchApprovalFlows } = await import('../../services/approvalService')
  vi.mocked(fetchApprovalFlows).mockResolvedValue([plainFlow, conditionalFlow] as never)
  renderWithProviders(<ApprovalFlowPage />)
  // 反空洞守卫：两个流名来自被 mock 的接口数据，出现即证明表格 request 已跑通、两行都已渲染。
  await screen.findByText('合同审批流', {}, { timeout: 5000 })
  await screen.findByText('报价单审批流', {}, { timeout: 5000 })
}

describe('ApprovalFlowPage「删除审批流」的权限收口（086）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('① ADMIN：两行的「删除」都可见（ADMIN 由 hasPerm 直通，与码无关）', async () => {
    await renderPage(adminUser)

    expect(queryDeletes()).toHaveLength(2)
  })

  it('② 零权限码的 SALES：页面真实内容照常渲染，唯独两处「删除」都不在', async () => {
    await renderPage(salesNoPerm)

    // 反空洞守卫：先钉住与权限无关的真实内容，否则"删除不在"可能只是整页没渲染出来
    expect(screen.getByText('合同审批流')).toBeInTheDocument()
    expect(screen.getByText('报价单审批流')).toBeInTheDocument()
    // 业务类型 Tag 走 labelOf(t, ENUM_KEYS.entity, ...)，两行分别是合同 / 报价单
    expect(screen.getByText('enums.entity.contract')).toBeInTheDocument()
    expect(screen.getByText('enums.entity.quote')).toBeInTheDocument()
    // 条件分支列：有 conditionJson 的那行渲染橙色 Tag，另一行是 '-'（占位与权限无关）
    expect(screen.getByText('pages.approvalFlow.amountCondition')).toBeInTheDocument()
    // 工具栏「新增审批流」与行内「编辑」刻意未收口（086 只收破坏性动作）
    expect(screen.getByRole('button', { name: /pages\.approvalFlow\.btnAdd/ })).toBeInTheDocument()
    expect(queryEdits()).toHaveLength(2)

    expect(queryDeletes()).toHaveLength(0)
  })

  it('③ 持 workflow:manage 的 SALES：两处「删除」都可见（锁住码的身份，非 ADMIN 亦可）', async () => {
    await renderPage(salesWithManage)

    expect(queryDeletes()).toHaveLength(2)
  })

  it('④ 只持形近的 workflow:delete（及别的页面的码）不构成放行——「删除」仍旧不在', async () => {
    await renderPage({ ...salesNoPerm, permissions: ['workflow:delete', 'announcement:manage'] })

    // 反空洞守卫：同一渲染里数据行与「编辑」都在，排除"页面空了"
    expect(screen.getByText('合同审批流')).toBeInTheDocument()
    expect(queryEdits()).toHaveLength(2)
    expect(queryDeletes()).toHaveLength(0)
  })

  it('⑤ 有码时「删除」确实接到删除端点：确认后以该行 id 调用 deleteApprovalFlow', async () => {
    const { deleteApprovalFlow } = await import('../../services/approvalService')
    vi.mocked(deleteApprovalFlow).mockResolvedValue(undefined)
    await renderPage(salesWithManage)

    fireEvent.click(queryDeletes()[0])
    // 确认气泡出现，说明这个链接确实是「删除」动作（而不是只剩个装饰性的红字）
    expect(await screen.findByText('pages.approvalFlow.confirmDelete', {}, { timeout: 5000 })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /确\s*定/ }))

    await waitFor(() => expect(deleteApprovalFlow).toHaveBeenCalledWith(plainFlow.id))
  })
})
