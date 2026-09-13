import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen, within } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import VisitListPage from './VisitListPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'
import type { FieldVisit } from '../../types/visit'

vi.mock('../../services/visitService', () => ({
  fetchVisits: vi.fn(),
  createVisit: vi.fn(),
  updateVisit: vi.fn(),
  cancelVisit: vi.fn(),
  checkInVisit: vi.fn(),
  fetchVisitStats: vi.fn(),
  getCurrentPosition: vi.fn(),
}))
// 本页只用到 customerService 的 fetchCustomers（下拉选项），一并替身化以免打真实网络。
vi.mock('../../services/customerService', () => ({
  fetchCustomers: vi.fn(),
}))

/**
 * 086 权限收口 · `VisitListPage` 行内「签到」「取消」的渲染测试。
 *
 * <p><b>两个动作挂同一个码</b>：签到打 `POST /field-visits/{id}/check-in`、取消打
 * `POST /field-visits/{id}/cancel`，两个端点在 `FieldVisitController.java:59-60` 与 `:67-68`
 * 上标的都是 `visit:manage`，所以页面里只有<b>一个</b>判据 `can[PERMS.visitManage]`
 * （`VisitListPage.tsx:58`）。这与 `TaskListPage` 那种"同页两码互不串码"的形态不同，
 * 本文件要锁的是另一件事：<b>同一个码同时管住两个控件</b>，且两个控件各自还带状态前提。
 *
 * <p><b>状态前提</b>（`VisitListPage.tsx:209,215`）：两个动作都嵌在 `row.status === 'PLANNED'`
 * 之内。同栏的「编辑」（`:214`）只有状态判据、没有权限判据（FR-B02：新建/编辑不在 086 收口范围）。
 * 故断言必须<b>按行状态分开取值</b>：拿 DONE 行说"看不见"是状态造成的，不能算权限的证据——
 * 第 ④⑤ 例专治这种混淆。
 *
 * <p>负向用例<b>必须</b>用非 ADMIN：`hasPerm` 对 `role === 'ADMIN'` 直接短路返回 true
 * （`hooks/usePermission.ts:10`），用管理员永远拿不到"看不见"的结论。
 */
const PLANNED_THEME = 'Q3 续约回访'
const DONE_THEME = '上月已完成的回访'

const makeVisit = (status: FieldVisit['status'], theme: string, extra: Partial<FieldVisit> = {}): FieldVisit => ({
  id: status === 'PLANNED' ? 1 : 2,
  customerId: 9,
  customerName: 'Acme 科技',
  theme,
  visitTime: '2026-09-20T10:00:00',
  status,
  ...extra,
})

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码。负向用例必须建立在它之上（ADMIN 在 hasPerm 里直通）。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 只多一个码，其余与基线逐字相同——把差异收敛到唯一变量。 */
const salesWithPerm: UserInfo = { ...salesNoPerm, permissions: ['visit:manage'] }

/**
 * 行内取值：`pages.visit.checkIn` 既是**列标题**又是**行内链接**，`screen.getByText` 会双命中直接抛错，
 * 所以必须先把范围缩到那一行（`within(row)`）再查。返回既可能是 `<a>` 也可能是 null，
 * 故统一用 `queryBy*` 并按 `toBeInTheDocument() / not.toBeInTheDocument()` 断言。
 */
const rowOf = (theme: string): HTMLElement => {
  const cell = screen.getByText(theme)
  const tr = cell.closest('tr')
  if (!tr) throw new Error(`未找到拜访行容器：${theme}`)
  return tr as HTMLElement
}

const checkInLink = (theme: string) => within(rowOf(theme)).queryByText('pages.visit.checkIn')
const cancelLink = (theme: string) => within(rowOf(theme)).queryByText('common.button.cancel')
/** 同栏里**不受权限管辖**的兄弟控件（只有状态判据），用来证明该栏确实渲染过了。 */
const editLink = (theme: string) => within(rowOf(theme)).queryByText('common.button.edit')

async function renderPage(user: UserInfo, status: FieldVisit['status']) {
  useAuthStore.setState({ user })
  const { fetchVisits, fetchVisitStats } = await import('../../services/visitService')
  const { fetchCustomers } = await import('../../services/customerService')

  const theme = status === 'PLANNED' ? PLANNED_THEME : DONE_THEME
  const row = makeVisit(status, theme, status === 'DONE' ? { checkInTime: '2026-09-10T10:00:00' } : {})
  vi.mocked(fetchCustomers).mockResolvedValue({ items: [], total: 0, page: 1, pageSize: 100 } as never)
  vi.mocked(fetchVisitStats).mockResolvedValue({ items: [], totalPlanned: 2, totalDone: 1, month: '2026-09' } as never)
  vi.mocked(fetchVisits).mockResolvedValue({ items: [row], total: 1, page: 1, pageSize: 10 } as never)

  renderWithProviders(<VisitListPage />)
  // 反空洞守卫：等到表格真的渲染出这一行（主题单元格由行数据渲染而来），
  // 后续的否定断言才有意义 —— 否则"链接不在"可能只是"列表还没拉回来"，那样的负向断言是假绿。
  await screen.findByText(theme, {}, { timeout: 5000 })
  return theme
}

describe('VisitListPage 收口（086）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('① ADMIN + PLANNED：「签到」「取消」都在（收口未误伤管理员）', async () => {
    const theme = await renderPage(adminUser, 'PLANNED')

    expect(checkInLink(theme)).toBeInTheDocument()
    expect(cancelLink(theme)).toBeInTheDocument()
  })

  it('② 无 visit:manage 的 SALES + PLANNED：数据行与「编辑」照常，两个动作都不可见', async () => {
    const theme = await renderPage(salesNoPerm, 'PLANNED')

    // 反空洞守卫：行内容在、同栏不受权限管辖的「编辑」也在 —— 证明这一行、这一栏都渲染过了，
    // 不见的确实只是那两个受 visit:manage 管辖的控件。
    expect(screen.getByText(theme)).toBeInTheDocument()
    expect(editLink(theme)).toBeInTheDocument()

    expect(checkInLink(theme)).not.toBeInTheDocument()
    expect(cancelLink(theme)).not.toBeInTheDocument()
  })

  it('③ 持 visit:manage 的 SALES + PLANNED：两个动作都可见（证明该码可授予，② 不是状态不对）', async () => {
    const theme = await renderPage(salesWithPerm, 'PLANNED')

    expect(screen.getByText(theme)).toBeInTheDocument() // 反空洞守卫
    expect(checkInLink(theme)).toBeInTheDocument()
    expect(cancelLink(theme)).toBeInTheDocument()
  })

  it('④ ADMIN + DONE：状态闸门独立生效——有全部权限也看不到这两个动作', async () => {
    const theme = await renderPage(adminUser, 'DONE')

    // 反空洞守卫：DONE 行不仅渲染了，连签到时间列都带出来了（证明这就是那条 DONE 行）
    expect(screen.getByText(theme)).toBeInTheDocument()
    expect(screen.getByText('2026-09-10 10:00')).toBeInTheDocument()

    // 管理员在 hasPerm 里直通 ⇒ 这两条"不可见"只可能来自状态判据，与权限无关
    expect(checkInLink(theme)).not.toBeInTheDocument()
    expect(cancelLink(theme)).not.toBeInTheDocument()
  })

  it('⑤ 持 visit:manage 的 SALES + DONE：与 ③ 同一用户、仅状态不同，动作仍不可见', async () => {
    const theme = await renderPage(salesWithPerm, 'DONE')

    expect(screen.getByText(theme)).toBeInTheDocument() // 反空洞守卫
    // 连只看状态的「编辑」也不在了 ⇒ 这确实是 DONE 行，不是"权限把整栏收没了"
    expect(editLink(theme)).not.toBeInTheDocument()

    expect(checkInLink(theme)).not.toBeInTheDocument()
    expect(cancelLink(theme)).not.toBeInTheDocument()
  })
})
