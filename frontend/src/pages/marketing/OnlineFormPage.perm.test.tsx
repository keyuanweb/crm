import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import OnlineFormPage from './OnlineFormPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/formService', () => ({
  fetchForms: vi.fn(),
  createForm: vi.fn(),
  updateForm: vi.fn(),
  deleteForm: vi.fn(),
  toggleForm: vi.fn(),
  fetchSubmissions: vi.fn(),
}))

/**
 * 086 权限收口 · `OnlineFormPage` 的**两个控件同一个码**的渲染测试。
 *
 * <pre>
 *   行内启用/停用 Switch ← form:manage（POST /forms/{id}/toggle）
 *   行内「删除」          ← form:manage（DELETE /forms/{id}）
 * </pre>
 *
 * <p>两个端点挂的是同一个码，界面上就不该出现第二个判据——所以这里两控件共用一组
 * 三向断言（ADMIN 可见 / 无码不可见 / 有码可见），任何一处漏判据或判错码都会让其中一条变红。
 *
 * <p>Switch 的计数靠 `role="switch"`：新建/编辑弹窗里的两个 Switch 都在 `open={false}` 且
 * `destroyOnClose` 的 Modal 内，不渲染，所以页面上出现的 switch **只可能是**行内那一只。
 * （若哪天弹窗改成常驻渲染，本文件的计数会立刻变成 3 而失败——这是刻意的。）
 *
 * <p>负向用例**必须**用非 ADMIN：`hasPerm` 对 `role === 'ADMIN'` 直接短路
 * （`hooks/usePermission.ts:10`），管理员永远拿不到"看不见"的结论。
 */
const formRow = {
  id: 1,
  name: '产品试用申请',
  source: 'WEBSITE',
  fields: JSON.stringify([{ field: 'name', label: '姓名', type: 'TEXT', required: true }]),
  submissionCount: 3,
  status: 'ENABLED',
}

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 只多了 `form:manage`，其余逐字相同。 */
const salesWithPerm: UserInfo = { ...salesNoPerm, permissions: ['form:manage'] }

const queryToggleSwitch = () => screen.queryAllByRole('switch')
const queryDelete = () => screen.queryAllByText('pages.marketing.onlineForm.btnDelete')

async function renderPage(user: UserInfo) {
  useAuthStore.setState({ user })
  const { fetchForms } = await import('../../services/formService')
  vi.mocked(fetchForms).mockResolvedValue([formRow] as never)

  renderWithProviders(<OnlineFormPage />)
  // 等到表格真的渲染出这一行，后续的否定断言才有意义 ——
  // 否则"控件不在"可能只是"页面还没加载出来"，那样的负向断言是假绿。
  await screen.findByText('产品试用申请')
}

/** 反空洞守卫：表单行与两个未收口的链接都真的渲染了。 */
function expectRowRendered() {
  expect(screen.getByText('产品试用申请')).toBeInTheDocument()
  expect(screen.getByText('pages.marketing.onlineForm.btnExternalLink')).toBeInTheDocument()
  expect(screen.getByText('pages.marketing.onlineForm.btnRecords')).toBeInTheDocument()
}

describe('OnlineFormPage 权限收口（086）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('ADMIN 看得见行内启用/停用 Switch 与「删除」', async () => {
    await renderPage(adminUser)

    expectRowRendered()
    expect(queryToggleSwitch()).toHaveLength(1)
    expect(queryDelete()).toHaveLength(1)
  })

  it('无 form:manage 的 SALES：行渲染出来了，但 Switch 与「删除」都不在', async () => {
    await renderPage(salesNoPerm)

    expectRowRendered()
    expect(queryToggleSwitch()).toHaveLength(0)
    expect(queryDelete()).toHaveLength(0)
  })

  it('持有 form:manage 的 SALES：看得见 Switch 与「删除」（该权限从此可授予）', async () => {
    await renderPage(salesWithPerm)

    expectRowRendered()
    expect(queryToggleSwitch()).toHaveLength(1)
    expect(queryDelete()).toHaveLength(1)
  })
})
