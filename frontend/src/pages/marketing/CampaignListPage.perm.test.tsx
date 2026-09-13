import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen, within } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import CampaignListPage from './CampaignListPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/marketingService', () => ({
  fetchCampaigns: vi.fn(),
  createCampaign: vi.fn(),
  updateCampaign: vi.fn(),
  startCampaign: vi.fn(),
  endCampaign: vi.fn(),
  deleteCampaign: vi.fn(),
  fetchChannelRoi: vi.fn(),
}))

/**
 * 087 补课 · `CampaignListPage` 的 create / update 双码渲染测试。
 *
 * <p>判据落点（映射表见 `specs/087-perm-test-backfill/plan.md`）：
 * <pre>
 *   工具栏「新增活动」  ← campaign:create                       （CampaignListPage.tsx:239）
 *   行内「开始」        ← campaign:update **∧ status==='PLANNING'**  （:185）
 *   行内「结束」        ← campaign:update **∧ status==='RUNNING'**   （:190）
 *   行内「编辑」        ← campaign:update **∧ status!=='ENDED'**     （:195）
 *   行内「删除」        ← campaign:delete（**本文件已覆盖**；**不带状态判据**，三行恒同）  （:200）
 * </pre>
 *
 * <p><b>本文件最容易写错的地方：把状态判据的差异误读成权限判据的差异。</b>
 * 「开始」在 PLANNING 行有、在 RUNNING 行没有，那是**状态**不是权限。所以表格里一次放
 * **三种状态各一行**，并且正/负向用例**共用同一份数据**——只让「权限变量」动。
 * 若只放一行，就会出现"以为权限把「开始」藏起来了、其实是这行状态不对"的假绿。
 *
 * <p>「编辑」那一支（非 ENDED）尤其要在 ENDED 行上反向钉一次：换了权限之后它**仍须不在**，
 * 否则说明状态判据被权限判据吞掉了。
 *
 * <p>负向用例**必须**用非 ADMIN：`hasPerm` 对 `role === 'ADMIN'` 直通返回 true
 * （`hooks/usePermission.ts:10`），拿管理员永远测不出「看不见」。
 *
 * <p><b>087-campaignDelete 补课（本文件第二条主线）：</b>该码此前被 086 的统计口径误判为「已覆盖」——
 * 它的字面量出现在**别页**的负向夹具（`LandingPageListPage.perm.test.tsx:104`）里，于是被当成有渲染用例；
 * 而本页真正的判据落点 `:200` 从未被执行过。
 * 补课时**沿用同一份三行数据**：删除是本页**唯一不带状态判据**的行内动作（对照：开始仅 PLANNING、
 * 结束仅 RUNNING、编辑非 ENDED，三者都带状态），所以删除在三种状态的行上表现一致——这既钉住了
 * 「删除不受状态管辖」，也让「删除不见了」唯一可归因于权限。正/负向两例的权限集合只差
 * `campaign:delete` **一个码**，数据其余部分逐字相同。
 */
const baseRow = {
  channel: 'WEBSITE' as const,
  budget: 1000,
  cost: 0,
  leadCount: 0,
  customerCount: 0,
  version: 0,
}
/** 三种状态各一行——状态判据必须由数据本身固定住，权限才成为唯一变量。 */
const planningRow = { ...baseRow, id: 1, name: '筹备活动', status: 'PLANNING' as const }
const runningRow = { ...baseRow, id: 2, name: '进行中活动', status: 'RUNNING' as const }
const endedRow = { ...baseRow, id: 3, name: '已结束活动', status: 'ENDED' as const }
const campaignRows = [planningRow, runningRow, endedRow]

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码——"看不见"的对照组必须建立在它之上。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 各只多一个码，其余与基线逐字相同——把差异收敛到唯一变量。 */
const salesWithCreate: UserInfo = { ...salesNoPerm, permissions: ['campaign:create'] }
const salesWithUpdate: UserInfo = { ...salesNoPerm, permissions: ['campaign:update'] }
/**
 * campaign:delete 的**唯一变量对照对**：与 `salesWithUpdate` 逐字相同，只多一个 `campaign:delete`。
 * 负向一例直接用 `salesWithUpdate`（它同时提供"同行「编辑」仍在"的行存在性证据），
 * 于是两例之间**只有这一个权限码在动**——状态、数据、其余权限全被钉死。
 */
const salesWithUpdateAndDelete: UserInfo = {
  ...salesNoPerm,
  permissions: ['campaign:update', 'campaign:delete'],
}

const TITLE = 'pages.marketing.campaign.title'
const BTN_CREATE = 'pages.marketing.campaign.btnCreate'
const BTN_START = 'pages.marketing.campaign.btnStart'
const BTN_END = 'pages.marketing.campaign.btnEnd'
const BTN_EDIT = 'pages.marketing.campaign.btnEdit'
const BTN_DELETE = 'pages.marketing.campaign.btnDelete'
/** 「渠道 ROI」是**读**（GET /campaigns/channel-roi 不设码）⇒ 恒在，用作工具栏的反空洞锚点。 */
const BTN_ROI = 'pages.marketing.campaign.btnRoi'

/** 取某一行（数据行的 `<tr>`），行内断言一律 `within` 它，免得跨行串门。 */
function rowOf(name: string): HTMLElement {
  const cell = screen.getByText(name)
  const tr = cell.closest('tr')
  if (!tr) throw new Error(`未找到数据行: ${name}`)
  return tr as HTMLElement
}

async function renderPage(user: UserInfo) {
  useAuthStore.setState({ user })
  const { fetchCampaigns } = await import('../../services/marketingService')
  vi.mocked(fetchCampaigns).mockResolvedValue({ items: campaignRows, total: 3, page: 1, pageSize: 20 } as never)

  renderWithProviders(<CampaignListPage />)
  // 反空洞守卫：等到表格真的渲染出三行（三种状态各一行），后续的否定断言才有意义 ——
  // 否则"按钮不在"可能只是"列表还没拉回来"，那样的负向断言是假绿。
  await screen.findByText(planningRow.name)
}

/**
 * 反空洞守卫：标题、工具栏「渠道 ROI」、三行数据都在 ⇒ 页面、工具栏与三种状态的行
 * 确实渲染过了。这是**非空性**证明（FR-004 允许它当锚点），**不得**用它充当"有权限"的证据。
 */
function expectPageRendered() {
  expect(screen.getByText(TITLE)).toBeInTheDocument()
  expect(screen.getByText(BTN_ROI)).toBeInTheDocument()
  expect(rowOf(planningRow.name)).toBeInTheDocument()
  expect(rowOf(runningRow.name)).toBeInTheDocument()
  expect(rowOf(endedRow.name)).toBeInTheDocument()
  // 状态列确实渲染成了三种不同状态（而不是三行同一状态）——状态变量本身也被钉住
  expect(screen.getByText('enums.campaignStatus.planning')).toBeInTheDocument()
  expect(screen.getByText('enums.campaignStatus.running')).toBeInTheDocument()
  expect(screen.getByText('enums.campaignStatus.ended')).toBeInTheDocument()
}

describe('CampaignListPage create/update 双码的渲染收口（087）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('ADMIN 看得见「新建」，且三行按**状态**各露出对应动作（与权限无关）', async () => {
    await renderPage(adminUser)

    expectPageRendered()
    expect(screen.getByText(BTN_CREATE)).toBeInTheDocument()
    // PLANNING 行：开始 + 编辑（结束被状态挡掉）
    expect(within(rowOf(planningRow.name)).queryByText(BTN_START)).toBeInTheDocument()
    expect(within(rowOf(planningRow.name)).queryByText(BTN_EDIT)).toBeInTheDocument()
    expect(within(rowOf(planningRow.name)).queryByText(BTN_END)).not.toBeInTheDocument()
    // RUNNING 行：结束 + 编辑（开始被状态挡掉）
    expect(within(rowOf(runningRow.name)).queryByText(BTN_END)).toBeInTheDocument()
    expect(within(rowOf(runningRow.name)).queryByText(BTN_EDIT)).toBeInTheDocument()
    expect(within(rowOf(runningRow.name)).queryByText(BTN_START)).not.toBeInTheDocument()
    // ENDED 行：三个动作都被状态挡掉——这一条是"状态判据独立于权限"的基准
    expect(within(rowOf(endedRow.name)).queryByText(BTN_START)).not.toBeInTheDocument()
    expect(within(rowOf(endedRow.name)).queryByText(BTN_END)).not.toBeInTheDocument()
    expect(within(rowOf(endedRow.name)).queryByText(BTN_EDIT)).not.toBeInTheDocument()
    // campaign:delete 对管理员直通
    expect(screen.getAllByText(BTN_DELETE)).toHaveLength(3)
  })

  // ---------------------------------------------------------------- campaign:create

  it('持有 campaign:create 的 SALES 看得见「新建」；三行的「开始」「结束」「编辑」「删除」都不在（双码互不串门）', async () => {
    await renderPage(salesWithCreate)

    expectPageRendered()
    expect(screen.getByText(BTN_CREATE)).toBeInTheDocument()
    expect(screen.queryAllByText(BTN_START)).toHaveLength(0)
    expect(screen.queryAllByText(BTN_END)).toHaveLength(0)
    expect(screen.queryAllByText(BTN_EDIT)).toHaveLength(0)
    // 同属"行内动作"的第四个码（campaign:delete）也不得被 campaign:create 顺带放行
    expect(screen.queryAllByText(BTN_DELETE)).toHaveLength(0)
  })

  it('无 campaign:create 的 SALES 看不见工具栏「新建」（工具栏与三行照常渲染）', async () => {
    await renderPage(salesNoPerm)

    expectPageRendered()
    expect(screen.queryByText(BTN_CREATE)).not.toBeInTheDocument()
  })

  // ---------------------------------------------------------------- campaign:update

  it('持有 campaign:update 的 SALES：三行按状态各露出对应动作；「新建」不在（双码互不串门）', async () => {
    await renderPage(salesWithUpdate)

    expectPageRendered()
    // PLANNING 行：开始 + 编辑 在，结束 不在（**状态**判据仍生效，没有被权限判据吞掉）
    expect(within(rowOf(planningRow.name)).queryByText(BTN_START)).toBeInTheDocument()
    expect(within(rowOf(planningRow.name)).queryByText(BTN_EDIT)).toBeInTheDocument()
    expect(within(rowOf(planningRow.name)).queryByText(BTN_END)).not.toBeInTheDocument()
    // RUNNING 行：结束 + 编辑 在，开始 不在
    expect(within(rowOf(runningRow.name)).queryByText(BTN_END)).toBeInTheDocument()
    expect(within(rowOf(runningRow.name)).queryByText(BTN_EDIT)).toBeInTheDocument()
    expect(within(rowOf(runningRow.name)).queryByText(BTN_START)).not.toBeInTheDocument()
    // ENDED 行：即使有 campaign:update，三个动作也**都必须不在**（非 ENDED 才给「编辑」）
    expect(within(rowOf(endedRow.name)).queryByText(BTN_START)).not.toBeInTheDocument()
    expect(within(rowOf(endedRow.name)).queryByText(BTN_END)).not.toBeInTheDocument()
    expect(within(rowOf(endedRow.name)).queryByText(BTN_EDIT)).not.toBeInTheDocument()
    // 互不串门：campaign:update 不得顺带放行工具栏「新建」
    expect(screen.queryByText(BTN_CREATE)).not.toBeInTheDocument()
  })

  it('无 campaign:update 的 SALES：三行的「开始」「结束」「编辑」都不在（状态差异不参与解释）', async () => {
    await renderPage(salesNoPerm)

    expectPageRendered()
    // 负荷性断言在 PLANNING / RUNNING 两行——它们**有码时本该露出**（见上一条）；
    // ENDED 行三支本来就被状态挡掉，那一支在本用例里是弱断言，故不单独依赖它。
    expect(within(rowOf(planningRow.name)).queryByText(BTN_START)).not.toBeInTheDocument()
    expect(within(rowOf(planningRow.name)).queryByText(BTN_EDIT)).not.toBeInTheDocument()
    expect(within(rowOf(runningRow.name)).queryByText(BTN_END)).not.toBeInTheDocument()
    expect(within(rowOf(runningRow.name)).queryByText(BTN_EDIT)).not.toBeInTheDocument()
    expect(within(rowOf(endedRow.name)).queryByText(BTN_EDIT)).not.toBeInTheDocument()
    expect(screen.queryAllByText(BTN_START)).toHaveLength(0)
    expect(screen.queryAllByText(BTN_END)).toHaveLength(0)
    expect(screen.queryAllByText(BTN_EDIT)).toHaveLength(0)
  })

  // ---------------------------------------------------------------- campaign:delete

  it('持有 campaign:delete 的 SALES：三行的「删除」都在，且 ENDED 行也有（该码**不带状态判据**）', async () => {
    await renderPage(salesWithUpdateAndDelete)

    expectPageRendered()
    // 删除是本页唯一不受状态管辖的行内动作，故三行（含 ENDED）表现一致；逐行 within 钉，
    // 免得"某一行有"被跨行串门掩盖成"三行都有"。
    expect(within(rowOf(planningRow.name)).queryByText(BTN_DELETE)).toBeInTheDocument()
    expect(within(rowOf(runningRow.name)).queryByText(BTN_DELETE)).toBeInTheDocument()
    expect(within(rowOf(endedRow.name)).queryByText(BTN_DELETE)).toBeInTheDocument()
    expect(screen.getAllByText(BTN_DELETE)).toHaveLength(3)
    // 行内的**状态**判据没有被这次权限断言吞掉：同样是这一份权限，ENDED 行的「编辑」仍被挡住。
    expect(within(rowOf(planningRow.name)).queryByText(BTN_EDIT)).toBeInTheDocument()
    expect(within(rowOf(endedRow.name)).queryByText(BTN_EDIT)).not.toBeInTheDocument()
  })

  it('无 campaign:delete 的 SALES：三行的「删除」都不在，而同行「编辑」仍在（行确实渲染过；非 ADMIN 才有否定效力）', async () => {
    await renderPage(salesWithUpdate)

    expectPageRendered()
    // 行存在性证据：同行的「编辑」（受 campaign:update 管辖、与本码无关）在 PLANNING / RUNNING
    // 两行仍在，而这两行**正是有码时删除会出现的那两行**；又因三行数据与上一条逐字相同，
    // 删除的消失只能归因于 campaign:delete。
    expect(within(rowOf(planningRow.name)).queryByText(BTN_EDIT)).toBeInTheDocument()
    expect(within(rowOf(runningRow.name)).queryByText(BTN_EDIT)).toBeInTheDocument()
    expect(within(rowOf(planningRow.name)).queryByText(BTN_DELETE)).not.toBeInTheDocument()
    expect(within(rowOf(runningRow.name)).queryByText(BTN_DELETE)).not.toBeInTheDocument()
    expect(within(rowOf(endedRow.name)).queryByText(BTN_DELETE)).not.toBeInTheDocument()
    expect(screen.queryAllByText(BTN_DELETE)).toHaveLength(0)
  })
})
