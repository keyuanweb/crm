import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import StageActionTemplatePage from './StageActionTemplatePage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/playbookService', () => ({
  fetchStageActions: vi.fn(),
  createStageAction: vi.fn(),
  updateStageAction: vi.fn(),
  deleteStageAction: vi.fn(),
}))
// 页面不直接 import 这个服务，但 `useOpportunityStages()` 的 queryFn 是它——
// 不挡掉就是一次真请求（XHR 已被 setup 静默成永不返回，阶段下拉会一直空着）。
vi.mock('../../services/opportunityStageService', () => ({
  fetchOpportunityStages: vi.fn(),
}))

/**
 * 086 权限收口 · `StageActionTemplatePage` 行内「删除」的渲染测试。
 *
 * <p>删除模板打的是 `DELETE /api/v1/stage-actions/{id}`，`PlaybookController` 上标的是
 * `playbook:manage`。三条断言缺一不可：ADMIN 可见 / 无码的非 ADMIN 不可见 / 只有该码的
 * 非 ADMIN 可见——第三条才证明「码本身起了作用」，否则「按钮不在」可能只是页面没渲染出来。
 *
 * <p>负向用例**必须**用非 ADMIN：`hasPerm` 对 `role === 'ADMIN'` 直通返回 true
 * （`hooks/usePermission.ts:10`），拿管理员永远测不出「看不见」。
 */
const templateRow = {
  id: 1,
  stage: 'NEGOTIATING',
  actionName: '提交报价单',
  description: '把报价单发给客户',
  sortOrder: 1,
  required: true,
  enabled: true,
  version: 0,
}

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码——"看不见"的对照组必须建立在它之上。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 只多了 `playbook:manage`，其余与基线逐字相同——把差异收敛到唯一变量。 */
const salesWithPerm: UserInfo = { ...salesNoPerm, permissions: ['playbook:manage'] }

/** 行内「删除」链接。 */
const queryDelete = () => screen.queryByText('common.button.delete')
/** 同一 action 列里**不受权限管辖**的兄弟控件，用来证明该列本身渲染出来了。 */
const queryEdit = () => screen.queryByText('common.button.edit')

async function renderPage(user: UserInfo) {
  useAuthStore.setState({ user })
  const { fetchStageActions } = await import('../../services/playbookService')
  const { fetchOpportunityStages } = await import('../../services/opportunityStageService')
  vi.mocked(fetchStageActions).mockResolvedValue({ items: [templateRow], total: 1, page: 1, pageSize: 20 } as never)
  vi.mocked(fetchOpportunityStages).mockResolvedValue([
    {
      id: 1,
      code: 'NEGOTIATING',
      name: '商务谈判',
      sortOrder: 1,
      probability: 0.6,
      stageType: 'ACTIVE' as const,
      enabled: 1,
      builtIn: false,
    },
  ] as never)

  renderWithProviders(<StageActionTemplatePage />)
  // 反空洞守卫：等到表格真的渲染出这一行，否定断言才有意义。
  await screen.findByText('提交报价单')
}

describe('StageActionTemplatePage 行内删除的权限收口（086）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('ADMIN 看得见行内「删除」', async () => {
    await renderPage(adminUser)

    expect(queryDelete()).toBeInTheDocument()
  })

  it('无 playbook:manage 的 SALES 看不见「删除」（页面与 action 列本身照常渲染）', async () => {
    await renderPage(salesNoPerm)

    // 反空洞守卫：数据行在，同一 action 列的「编辑」链接也在——证明列渲染过了，
    // 不见的确实只是那个受权限管辖的控件。
    expect(screen.getByText('提交报价单')).toBeInTheDocument()
    expect(queryEdit()).toBeInTheDocument()
    expect(queryDelete()).not.toBeInTheDocument()
  })

  it('持有 playbook:manage 的 SALES 看得见「删除」（证明该码可授予）', async () => {
    await renderPage(salesWithPerm)

    expect(queryDelete()).toBeInTheDocument()
  })
})
