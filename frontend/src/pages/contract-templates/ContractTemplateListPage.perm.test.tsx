import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import ContractTemplateListPage from './ContractTemplateListPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/contractService', () => ({
  fetchContractTemplates: vi.fn(),
  createContractTemplate: vi.fn(),
  updateContractTemplate: vi.fn(),
  deleteContractTemplate: vi.fn(),
}))

/**
 * 086 权限收口 · `ContractTemplateListPage` 的双向渲染测试。
 *
 * <p>本页是全批里唯一一处**把 `role === 'ADMIN'` 硬编码换成权限码**的改动，所以它需要
 * 三个方向上的断言，缺一不可：
 *
 * <ol>
 *   <li><b>ADMIN 仍看得见</b>——证明这次改写没有把管理员的按钮改掉（`hasPerm` 对 ADMIN 直通）；
 *   <li><b>无码的非 ADMIN 看不见</b>——证明"严格隐藏"生效（这是收口的目的）；
 *   <li><b>有码的非 ADMIN 看得见</b>——<b>这一条才是改写的意义所在</b>。`role === 'ADMIN'`
 *       这个写法**永远授不出去**：管理员即便在角色页给某个角色勾上 `contract_template:manage`，
 *       硬编码判断也只会看角色名。第三条断言证明该权限从此真的可以被授予。
 * </ol>
 *
 * <p>负向用例**必须**用非 ADMIN 用户：`hasPerm` 对 `role === 'ADMIN'` 直接短路返回 true
 * （`hooks/usePermission.ts:10`），用管理员永远拿不到"看不见"的结论。
 */
const templateRow = { id: 1, name: '标准销售合同', content: '甲方……', status: 'ACTIVE', version: 0 }

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }

/** 非 ADMIN 基线：一条权限码都没有——"看不见"的对照组必须建立在它之上。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }

/** 只多了这一个码，其余与 `salesNoPerm` 逐字相同——把差异**收敛到唯一变量**。 */
const salesWithPerm: UserInfo = {
  ...salesNoPerm,
  permissions: ['contract_template:manage'],
}

const withUser = (user: UserInfo) => useAuthStore.setState({ user })

/** 工具栏「新建」按钮：全页唯一，用 role+name 定位最稳。 */
const queryCreateButton = () => screen.queryByRole('button', { name: /pages\.contractTemplate\.btnAdd/ })
/** 行内「编辑」链接。`EditOutlined` 是 svg 不含文本，故 `<a>` 的可见文本恰是这个键名。 */
const queryEditLink = () => screen.queryByText('pages.contractTemplate.edit')

async function renderPage() {
  const { fetchContractTemplates } = await import('../../services/contractService')
  vi.mocked(fetchContractTemplates).mockResolvedValue({ items: [templateRow], total: 1, page: 1, pageSize: 20 } as never)
  renderWithProviders(<ContractTemplateListPage />)
  // 等到表格真的渲染出这一行，后续的否定断言才有意义 ——
  // 否则"按钮不在"可能只是"页面还没加载出来"，那样的负向断言是假绿。
  await screen.findByText('标准销售合同')
}

describe('ContractTemplateListPage 权限收口（086）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('ADMIN 看得见「新建」与「编辑」（改写未改变管理员行为）', async () => {
    withUser(adminUser)
    await renderPage()

    expect(queryCreateButton()).toBeInTheDocument()
    expect(queryEditLink()).toBeInTheDocument()
  })

  it('无 contract_template:manage 的 SALES 看不见「新建」与「编辑」', async () => {
    withUser(salesNoPerm)
    await renderPage()

    // 先确认页面确实渲染好了（同一行数据可见），否定断言才不是假绿
    expect(screen.getByText('标准销售合同')).toBeInTheDocument()
    expect(queryCreateButton()).not.toBeInTheDocument()
    expect(queryEditLink()).not.toBeInTheDocument()
  })

  it('持有 contract_template:manage 的 SALES 看得见「新建」与「编辑」（该权限从此可授予）', async () => {
    withUser(salesWithPerm)
    await renderPage()

    expect(queryCreateButton()).toBeInTheDocument()
    expect(queryEditLink()).toBeInTheDocument()
  })
})
