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
 * <p><b>086 刻意不测同页的 `canAssign`</b>：那是既有的判据，不属于本次收口范围，
 * 改了它或者给它加断言都会把本次收口的"失败"含义弄混。
 *
 * <p><b>087 追补（T015）</b>：`lead:assign` 正属于"早已接线但零渲染层用例"的那 15 个码之一。
 * 它与本页的 `lead:delete` 同页，故**追加**在文件末尾的第二个 `describe` 里单独覆盖，
 * 而不是新建第二个测同一页面的文件；上面 086 的三条用例逐字未动。
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

/**
 * 渲染页面并等数据行落地。
 *
 * @param user 当前登录用户（负向用例必须是非 ADMIN）。
 * @param row  数据行；默认是上面那条 `ownerId: 99` 的基线行（086 的三条用例都用默认值，
 *             行为与从前逐字一致）。087 的「归属判据」用例靠它换 `ownerId`。
 */
async function renderPage(user: UserInfo, row: typeof leadRow = leadRow) {
  withUser(user)
  const { fetchLeads } = await import('../../services/leadService')
  vi.mocked(fetchLeads).mockResolvedValue({ items: [row], total: 1, page: 1, pageSize: 20 } as never)
  renderWithProviders(<LeadListPage />)
  // 等到表格真的渲染出这一行，后续的否定断言才有意义 ——
  // 否则"链接不在"可能只是"页面还没加载出来"，那样的负向断言是假绿。
  await screen.findByText(row.name)
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

// ---------------------------------------------------------------------------
// 087 追补（T015）：lead:assign
// ---------------------------------------------------------------------------

/**
 * 087 追补 · `LeadListPage` 行内「分配给我」的渲染测试（`lead:assign`）。
 *
 * <p>判据在 `LeadListPage.tsx:84`：`const canAssign = hasPerm(PERMS.leadAssign, user)`（**早于 086 就存在**，
 * 它把改造前的 `role === 'ADMIN'` 硬编码换成了权限码——否则 081 新增的角色里凡是拿到 `lead:assign`
 * 的（如 SALES_MANAGER）在列表上根本看不到这个动作）。落点是 `:264`。
 *
 * <p><b>本码最容易被写错的地方：它是 ∧ 的</b>。`:264` 的条件是
 * `row.ownerId != null && row.ownerId !== user?.id && canAssign`——**归属判据在前、权限判据在后**。
 * 所以：
 *
 * <ul>
 *   <li>夹具行的 `ownerId` **必须**非空、且**不等于**当前用户 id，否则控件本就不可见，
 *       权限变量再动也测不出东西（用例 ② 会**假绿**）；</li>
 *   <li>用例 ④ 是那条 ∧ 的正交证明：**持码**但 `ownerId === user.id`（这行就是本人持有的）时
 *       仍不可见——证明归属闸门没有被权限判据吞掉。把所有权判据改成恒真，它立刻转红。</li>
 * </ul>
 *
 * <p><b>锚点防真空</b>：每条用例都同时断言数据行与同处操作列的「编辑」链接在文档里
 * （`canEdit` 与权限无关，只要 `status` 不是 QUALIFIED/DISQUALIFIED 就渲染）——
 * 否则整页渲染失败会被误判成"按钮正确地不在"，那种负向断言是假绿。
 *
 * <p>负向用例**必须**用非 ADMIN：`hasPerm` 对 `role === 'ADMIN'` 直通返回 true
 * （`hooks/usePermission.ts:10`），管理员永远测不出「看不见」。
 */
/** 非 ADMIN 基线（`salesNoPerm`）只多了 `lead:assign`，其余逐字相同——把差异收敛到唯一变量。 */
const salesWithAssign: UserInfo = { ...salesNoPerm, permissions: ['lead:assign'] }

/** 行内「分配给我」链接（`SwapOutlined` 是 svg 不含文本，故 `<a>` 的可见文本恰是这个键名）。 */
const queryAssign = () => screen.queryByText('pages.lead.list.assignToMe')

describe('LeadListPage 行内「分配给我」的权限接线（087 T015 · lead:assign）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('① ADMIN 看得见「分配给我」（锚点：数据行与「编辑」都在）', async () => {
    await renderPage(adminUser)

    expect(screen.getByText('线索甲')).toBeInTheDocument()
    expect(queryEdit()).toBeInTheDocument()
    expect(queryAssign()).toBeInTheDocument()
  })

  it('② 无 lead:assign 的 SALES 看不见「分配给我」，同行的「编辑」仍在（负向）', async () => {
    await renderPage(salesNoPerm)

    // 反空洞守卫：该行确实渲染了，且操作列本身跑到了——消失的只有被权限管辖的那一个链接。
    // 注意夹具行的 ownerId 是 99（≠ 当前用户 id 2），归属判据**满足**，
    // 所以这里"看不见"只可能来自权限判据本身。
    expect(screen.getByText('线索甲')).toBeInTheDocument()
    expect(queryEdit()).toBeInTheDocument()
    expect(queryAssign()).not.toBeInTheDocument()
  })

  it('③ 持有 lead:assign 的 SALES 看得见「分配给我」（正向；锁住码的身份）', async () => {
    await renderPage(salesWithAssign)

    expect(screen.getByText('线索甲')).toBeInTheDocument()
    expect(queryEdit()).toBeInTheDocument()
    expect(queryAssign()).toBeInTheDocument()
  })

  it('④ 持码但该行归属就是本人（ownerId === 当前用户 id）⇒ 仍不可见（归属闸门未被权限吞掉）', async () => {
    await renderPage(salesWithAssign, { ...leadRow, ownerId: salesWithAssign.id })

    // 锚点：行与「编辑」都在，页面确实渲染了；唯一变量是 ownerId 变成了本人。
    expect(screen.getByText('线索甲')).toBeInTheDocument()
    expect(queryEdit()).toBeInTheDocument()
    expect(queryAssign()).not.toBeInTheDocument()
  })

  it('⑤ 互不串门：只授 lead:delete 的 SALES 能删、但拿不到「分配给我」', async () => {
    // 本页由**两个码**分别管两个控件（086 的 lead:delete 与 087 的 lead:assign）。
    // 这条用例锁住它们不互相顶替——若有人把 canAssign 写成 canDelete（或反之），它立刻红。
    await renderPage(salesWithDelete)

    expect(screen.getByText('线索甲')).toBeInTheDocument()
    expect(queryEdit()).toBeInTheDocument()
    expect(queryDelete()).toBeInTheDocument()
    expect(queryAssign()).not.toBeInTheDocument()
  })
})
