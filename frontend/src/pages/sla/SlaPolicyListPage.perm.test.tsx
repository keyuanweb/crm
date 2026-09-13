import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import SlaPolicyListPage from './SlaPolicyListPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/slaService', () => ({
  fetchSlaPolicies: vi.fn(),
  createSlaPolicy: vi.fn(),
  updateSlaPolicy: vi.fn(),
  deleteSlaPolicy: vi.fn(),
  fetchSlaOverview: vi.fn(),
}))

/**
 * 086 权限收口 · `SlaPolicyListPage` 行内「删除」的渲染测试。
 *
 * <p>删除策略打的是 `DELETE /api/v1/sla-policies/{id}`，`SlaPolicyController` 上标的是 `sla:manage`。
 * 三条断言缺一不可：ADMIN 可见 / 无码的非 ADMIN 不可见 / 只有该码的非 ADMIN 可见——
 * 第三条才证明「码本身起了作用」，否则「按钮不在」可能只是页面没渲染出来。
 *
 * <p>负向用例**必须**用非 ADMIN：`hasPerm` 对 `role === 'ADMIN'` 直通返回 true
 * （`hooks/usePermission.ts:10`），拿管理员永远测不出「看不见」。
 */
const policyRow = { id: 1, priority: 'HIGH', respondHours: 2, resolveHours: 8, enabled: 1, version: 0 }

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码——"看不见"的对照组必须建立在它之上。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 只多了 `sla:manage`，其余与基线逐字相同——把差异收敛到唯一变量。 */
const salesWithPerm: UserInfo = { ...salesNoPerm, permissions: ['sla:manage'] }

/** 行内「删除」链接。 */
const queryDelete = () => screen.queryByText('pages.slaPolicy.delete')
/** 同一 action 列里**不受权限管辖**的兄弟控件，用来证明该列本身渲染出来了。 */
const queryEdit = () => screen.queryByText('pages.slaPolicy.edit')

async function renderPage(user: UserInfo) {
  useAuthStore.setState({ user })
  const { fetchSlaPolicies, fetchSlaOverview } = await import('../../services/slaService')
  vi.mocked(fetchSlaPolicies).mockResolvedValue({ items: [policyRow], total: 1, page: 1, pageSize: 10 } as never)
  vi.mocked(fetchSlaOverview).mockResolvedValue({ totalOpen: 3, overdue: 1, byPriority: [] } as never)

  renderWithProviders(<SlaPolicyListPage />)
  // 反空洞守卫：等到表格真的渲染出这一行（优先级 Tag 由行数据渲染而来），否定断言才有意义。
  await screen.findByText('enums.priority.high')
}

describe('SlaPolicyListPage 行内删除的权限收口（086）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('ADMIN 看得见行内「删除」', async () => {
    await renderPage(adminUser)

    expect(queryDelete()).toBeInTheDocument()
  })

  it('无 sla:manage 的 SALES 看不见「删除」（页面与 action 列本身照常渲染）', async () => {
    await renderPage(salesNoPerm)

    // 反空洞守卫：数据行在，同一 action 列的「编辑」链接也在——证明列渲染过了，
    // 不见的确实只是那个受权限管辖的控件。
    expect(screen.getByText('enums.priority.high')).toBeInTheDocument()
    expect(queryEdit()).toBeInTheDocument()
    expect(queryDelete()).not.toBeInTheDocument()
  })

  it('持有 sla:manage 的 SALES 看得见「删除」（证明该码可授予）', async () => {
    await renderPage(salesWithPerm)

    expect(queryDelete()).toBeInTheDocument()
  })
})
