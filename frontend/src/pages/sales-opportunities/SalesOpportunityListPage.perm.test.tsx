import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import SalesOpportunityListPage from './SalesOpportunityListPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/opportunityService', () => ({
  fetchSalesOpportunities: vi.fn(),
  fetchOpportunities: vi.fn(async () => ({ items: [], total: 0, page: 1, pageSize: 100 })),
  createSalesOpportunity: vi.fn(),
  updateSalesOpportunity: vi.fn(),
  closeSalesOpportunity: vi.fn(),
}))
// 阶段字典（`useOpportunityStages`）在挂载时就发请求：不打桩会走未打桩的网络层（测试里挂起不返回），
// 且 `isTerminal` 会一直退回编码兜底分支，测试就不再验证真实的字典路径了。
vi.mock('../../services/opportunityStageService', () => ({
  fetchOpportunityStages: vi.fn(async () => [
    { id: 1, code: 'NEGOTIATION', name: '谈判中', sortOrder: 1, probability: 60, stageType: 'ACTIVE', enabled: 1, builtIn: true },
    { id: 2, code: 'CLOSED_WON', name: '赢单', sortOrder: 9, probability: 100, stageType: 'WON', enabled: 1, builtIn: true },
    { id: 3, code: 'CLOSED_LOST', name: '输单', sortOrder: 10, probability: 0, stageType: 'LOST', enabled: 1, builtIn: true },
  ]),
  createOpportunityStage: vi.fn(),
  updateOpportunityStage: vi.fn(),
  setOpportunityStageEnabled: vi.fn(),
  deleteOpportunityStage: vi.fn(),
}))

/**
 * 086 权限收口 · `SalesOpportunityListPage`「赢单 / 输单」的渲染测试。
 *
 * <p>本页的判据只有一道码（`opportunity:update`，`SalesOpportunityListPage.tsx:54,144`），但
 * 它与**另一道与权限无关的闸门**叠在同一个三元里（`:143`）：
 *
 * <pre>
 *   !isTerminal(row.stage) ? (can[opportunity:update] ? [赢单, 输单] : null) : [已关闭]
 * </pre>
 *
 * <p>所以「按钮不在」有两个可能的原因：没权限，或这条商机已是终态。中间那一支（非终态 + 无权限）
 * 渲染的是 `null`，最容易与「还没加载完」混淆——故每条否定断言前都先确认商机行真的渲染出来了。
 * 为此第 ④ 例专门用一条**终态行**做对照：同样是无权限，非终态行整格为空，终态行则必须显示
 * 「已关闭」——两例一起才能说明「空的来源是权限，而不是这一列本来就不渲染」。
 *
 * <p>负向用例**必须**用非 ADMIN 用户：`hasPerm` 对 `role === 'ADMIN'` 直接短路返回 true
 * （`hooks/usePermission.ts:10`），用管理员永远拿不到「看不见」的结论。
 */

const OPP_NAME = 'Acme 年度续约'

const makeRow = (stage: string) => ({
  id: 1,
  opportunityId: 100,
  opportunityName: OPP_NAME,
  customerName: 'Acme 科技',
  amount: 100000,
  stage,
  version: 0,
})

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }

/** 非 ADMIN 基线：零权限码。负向用例必须建立在它之上（ADMIN 在 hasPerm 里直通）。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 只多这一个码，其余与基线逐字相同——把差异收敛到唯一变量。 */
const salesWithPerm: UserInfo = { ...salesNoPerm, permissions: ['opportunity:update'] }

const linkWon = () => screen.queryByText('pages.salesOpportunity.btnWon')
const linkLost = () => screen.queryByText('pages.salesOpportunity.btnLost')

async function renderPage(user: UserInfo, stage = 'NEGOTIATION') {
  useAuthStore.setState({ user })
  const { fetchSalesOpportunities } = await import('../../services/opportunityService')
  vi.mocked(fetchSalesOpportunities).mockResolvedValue({
    items: [makeRow(stage)],
    total: 1,
    page: 1,
    pageSize: 20,
  } as never)

  renderWithProviders(<SalesOpportunityListPage />)
  // 等到表格真的渲染出这一行，后续的否定断言才有意义 ——
  // 否则「按钮不在」可能只是「页面还没加载出来」，那样的负向断言是假绿。
  await screen.findByText(OPP_NAME, {}, { timeout: 5000 })
}

describe('SalesOpportunityListPage 赢单/输单收口（086）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('① ADMIN：赢单 / 输单 都看得见（收口未误伤管理员）', async () => {
    await renderPage(adminUser)

    expect(linkWon()).toBeInTheDocument()
    expect(linkLost()).toBeInTheDocument()
  })

  it('② 无 opportunity:update 的 SALES：赢单 / 输单 都不可见（先确认页面已渲染）', async () => {
    await renderPage(salesNoPerm)

    // 反空洞守卫：商机行确实渲染出来了，否定断言才不是假绿
    expect(screen.getByText(OPP_NAME)).toBeInTheDocument()
    expect(screen.getByText('Acme 科技')).toBeInTheDocument()

    expect(linkWon()).not.toBeInTheDocument()
    expect(linkLost()).not.toBeInTheDocument()
  })

  it('③ 持 opportunity:update 的 SALES：赢单 / 输单 都可见（该权限从此可授予）', async () => {
    await renderPage(salesWithPerm)

    expect(linkWon()).toBeInTheDocument()
    expect(linkLost()).toBeInTheDocument()
  })

  it('④ 对照：无权限 + **终态**行 ⇒ 这一格仍有内容（「已关闭」），不是整格不渲染', async () => {
    await renderPage(salesNoPerm, 'CLOSED_WON')

    expect(screen.getByText(OPP_NAME)).toBeInTheDocument() // 反空洞守卫
    // 终态分支优先于权限分支：无权限也照样显示「已关闭」，只是没有任何动作入口。
    // 这一条把第 ② 例的「空」钉死在权限上，而不是「这一列本来就不渲染」。
    expect(screen.getByText('pages.salesOpportunity.statusClosed')).toBeInTheDocument()
    expect(linkWon()).not.toBeInTheDocument()
    expect(linkLost()).not.toBeInTheDocument()
  })
})
