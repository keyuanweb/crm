import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen, within } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import OpportunityStagePage from './OpportunityStagePage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

// 页面本身不直接 import 这个服务，但 `useOpportunityStages()` 的 queryFn 是它——
// 不挡掉就是一次真请求（XHR 已被 setup 静默成永不返回，列表会一直空着）。
vi.mock('../../services/opportunityStageService', () => ({
  fetchOpportunityStages: vi.fn(),
  createOpportunityStage: vi.fn(),
  updateOpportunityStage: vi.fn(),
  setOpportunityStageEnabled: vi.fn(),
  deleteOpportunityStage: vi.fn(),
}))

/**
 * 086 权限收口 · `OpportunityStagePage` 的「停用 / 启用 / 删除」渲染测试。
 *
 * <p>停用/启用走 `POST /opportunity-stages/{id}/enabled`、删除走 `DELETE /opportunity-stages/{id}`，
 * `OpportunityStageController` 上两个方法挂的都是 `stage:manage`。
 *
 * <p>本页的判据**不是** `can[码] && 动作` 这么简单，每个动作外面还叠了一层**业务状态三元**
 * （`OpportunityStagePage.tsx:193-220`）：
 *
 * <pre>
 *   停用：can && row.enabled === 1              // 只有启用中的行才可停用
 *   启用：can && row.enabled !== 1              // 只有停用中的行才可启用
 *   删除：row.builtIn || row.enabled === 1 ? 灰色 <span>占位
 *         : can ? <Popconfirm><a/></Popconfirm>
 *         : null
 * </pre>
 *
 * <p>于是「看不见」有**两种成因**，本文件分别断言、不混为一谈：
 * <ul>
 *   <li><b>权限造成的不可见</b>——`null`，元素根本不存在（无码的行 2「删除」、无码的「停用/启用」）；</li>
 *   <li><b>状态造成的不可见</b>——灰色 `<span>` 占位，文案与真按钮**逐字相同**，但不可点，
 *       且**与权限无关**（内建终态行、启用中的行）。第 2 例专门锁这一点：无码时它照样在，
 *       证明权限判据没有把状态分支一起吞掉。</li>
 * </ul>
 *
 * <p>两种形态在 DOM 里的唯一差别是标签名（可执行的是 `<a>`，占位是 `<span>`），故用
 * `within(行)` + 标签名区分，而不是全局按文案查——全局查会把两者一起命中。
 *
 * <p>负向用例**必须**用非 ADMIN：`hasPerm` 对 `role === 'ADMIN'` 直通返回 true
 * （`hooks/usePermission.ts:10`），拿管理员永远测不出「看不见」。
 */
const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码——"看不见"的对照组必须建立在它之上。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 只多了 `stage:manage`，其余与基线逐字相同——把差异收敛到唯一变量。 */
const salesWithPerm: UserInfo = { ...salesNoPerm, permissions: ['stage:manage'] }

/** 启用中、可停用、可删（先停用才行）的行。编码不是内建编码，故列表显示它自己的 name。 */
const enabledStage = {
  id: 1,
  code: 'BUDGET_APPROVAL',
  name: '预算审批',
  sortOrder: 1,
  probability: 0.5,
  stageType: 'ACTIVE' as const,
  enabled: 1,
  builtIn: false,
}
/** 已停用、可启用、可删（删除按钮真的可执行）的行。 */
const disabledStage = {
  id: 2,
  code: 'TECH_REVIEW',
  name: '技术评审',
  sortOrder: 2,
  probability: 0.6,
  stageType: 'ACTIVE' as const,
  enabled: 0,
  builtIn: false,
}
/** 内建终态：不可删、不可改编码——删除位永远是灰色占位，与权限无关。 */
const builtInStage = {
  id: 3,
  code: 'CLOSED_WON',
  name: '赢单',
  sortOrder: 3,
  probability: 1,
  stageType: 'WON' as const,
  enabled: 1,
  builtIn: true,
}

const BTN_DISABLE = 'pages.opportunityStageSettings.btnDisable'
const BTN_ENABLE = 'pages.opportunityStageSettings.btnEnable'
const BTN_DELETE = 'pages.opportunityStageSettings.btnDelete'

/** 按该行的阶段名单元格定位到 `<tr>`——「删除」在同一份文档里有 `<a>` 与 `<span>` 两种，
 *  必须行内查询才能分辨，全局按文案查是分不出来的。 */
function rowOf(renderedName: string): HTMLElement {
  const tr = screen.getByText(renderedName).closest('tr')
  if (!tr) throw new Error(`未找到「${renderedName}」所在的行`)
  return tr as HTMLElement
}

/** 该行里**可执行**的删除控件（`<a>`，被 Popconfirm 包着）。 */
function executableDeleteIn(row: HTMLElement) {
  return within(row).queryAllByText(BTN_DELETE).find((el) => el.tagName === 'A') ?? null
}

/** 该行里的**灰色删除占位**（`<span>`，不可点、与权限无关）。 */
function placeholderDeleteIn(row: HTMLElement) {
  return within(row).queryAllByText(BTN_DELETE).find((el) => el.tagName === 'SPAN') ?? null
}

async function renderPage(user: UserInfo) {
  useAuthStore.setState({ user })
  const { fetchOpportunityStages } = await import('../../services/opportunityStageService')
  vi.mocked(fetchOpportunityStages).mockResolvedValue([
    enabledStage,
    disabledStage,
    builtInStage,
  ] as never)

  renderWithProviders(<OpportunityStagePage />)
  // 反空洞守卫：三行都渲染出来（内建终态行按内建文案显示），否定断言才有意义 ——
  // 否则"按钮不在"可能只是"页面还没加载出来"，那样的负向断言是假绿。
  await screen.findByText('预算审批')
  await screen.findByText('技术评审')
  await screen.findByText('enums.opportunityStage.closedWon')
}

describe('OpportunityStagePage 停用/启用/删除的权限收口（086）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('ADMIN：可执行的停用/启用/删除都在，状态占位照旧', async () => {
    await renderPage(adminUser)

    const enabledRow = rowOf('预算审批')
    const disabledRow = rowOf('技术评审')
    const builtInRow = rowOf('enums.opportunityStage.closedWon')

    expect(within(enabledRow).queryByText(BTN_DISABLE)).toBeInTheDocument()
    expect(within(enabledRow).queryByText(BTN_ENABLE)).not.toBeInTheDocument()
    expect(within(disabledRow).queryByText(BTN_ENABLE)).toBeInTheDocument()
    expect(within(disabledRow).queryByText(BTN_DISABLE)).not.toBeInTheDocument()
    expect(executableDeleteIn(disabledRow)).not.toBeNull()
    // 启用中 / 内建终态的行：删除位是占位，不是可执行动作
    expect(placeholderDeleteIn(enabledRow)).not.toBeNull()
    expect(placeholderDeleteIn(builtInRow)).not.toBeNull()
    expect(executableDeleteIn(enabledRow)).toBeNull()
    expect(executableDeleteIn(builtInRow)).toBeNull()
  })

  it('无 stage:manage 的 SALES：三种动作都不可见；但状态占位仍在（两种不可见成因不同）', async () => {
    await renderPage(salesNoPerm)

    const enabledRow = rowOf('预算审批')
    const disabledRow = rowOf('技术评审')

    // 反空洞守卫：三行数据都在（renderPage 已等过），且 action 列本身渲染了——
    // 「编辑」不受权限管辖，它在此处是那一列"确实渲染过"的证据。
    expect(within(disabledRow).queryByText('pages.opportunityStageSettings.btnEdit')).toBeInTheDocument()

    // 权限造成的不可见：元素根本不存在
    expect(within(enabledRow).queryByText(BTN_DISABLE)).not.toBeInTheDocument()
    expect(within(disabledRow).queryByText(BTN_ENABLE)).not.toBeInTheDocument()
    expect(executableDeleteIn(disabledRow)).toBeNull()
    // 状态造成的不可见：与权限无关的灰色占位，无码时照样在
    expect(placeholderDeleteIn(enabledRow)).not.toBeNull()
  })

  it('持有 stage:manage 的 SALES：停用/启用/删除都可见（证明该码可授予）', async () => {
    await renderPage(salesWithPerm)

    const enabledRow = rowOf('预算审批')
    const disabledRow = rowOf('技术评审')

    expect(within(enabledRow).queryByText(BTN_DISABLE)).toBeInTheDocument()
    expect(within(disabledRow).queryByText(BTN_ENABLE)).toBeInTheDocument()
    expect(executableDeleteIn(disabledRow)).not.toBeNull()
  })
})
