import { describe, expect, it, vi, beforeEach } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import CustomerListPage from './CustomerListPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

/*
  只放宽**超时上限**，不放宽任何断言——判据写错时这些用例仍然是红的（13 条已逐条变异自验）。

  为什么要放：本文件每条用例都要整页渲染（ProTable + 自定义字段列 + 两处 user/campaign 取数），
  在 `pnpm run test:coverage` 的 72 文件并行下实测比单跑慢约 6.6 倍——单跑 15.4s/13 例（1.19s/例），
  全量 102.1s/13 例（7.85s/例）；而「切到公海视图」那几条要连续渲染两轮，于是撞上 vitest 全局的
  `testTimeout: 20000`。2026-09-13 连续两次覆盖率运行**都**在这一条（`customer:claim` 公海视图正向）
  超时；同一文件单跑 13/13 全绿。即：红的成因是 CPU 争用，不是判据。
  （`vi.setConfig` 在模块作用域生效已用探针实证，不是照文档假设的。）
*/
vi.setConfig({ testTimeout: 60_000 })

vi.mock('../../services/customerService', () => ({
  fetchCustomers: vi.fn(),
  createCustomer: vi.fn(),
  updateCustomer: vi.fn(),
  deleteCustomer: vi.fn(),
  fetchMyCustomers: vi.fn(),
  fetchPoolCustomers: vi.fn(),
  batchTransferCustomers: vi.fn(),
  claimCustomer: vi.fn(),
  exportCustomers: vi.fn(),
  importCustomers: vi.fn(),
  downloadTemplate: vi.fn(),
  scanPool: vi.fn(),
}))
vi.mock('../../services/userService', () => ({
  fetchUsers: vi.fn(async () => ({ items: [], total: 0, page: 1, pageSize: 20 })),
}))
vi.mock('../../services/marketingService', () => ({
  fetchCampaigns: vi.fn(async () => ({ items: [], total: 0, page: 1, pageSize: 100 })),
}))
vi.mock('../../hooks/useCustomFieldFilters', () => ({
  useCustomFieldFilterColumns: () => [],
  extractCfParams: () => ({}),
}))
vi.mock('../../components/CustomFieldItems', () => ({
  CustomFieldFormItems: () => null,
}))
vi.mock('../../utils/customField', () => ({
  fromCustomFieldValues: () => ({}),
  toCustomFieldPayload: () => ({}),
}))

/**
 * 087 补课 · `CustomerListPage` 五个权限码的**双向**渲染测试。
 *
 * <p>本页是 086 盘点出「已接线但零渲染层用例」的 15 个码里唯一**一页占五码**的页面，
 * 而且带着两个非显然点，所以它是本规格最值得逐个钉死的一页：
 *
 * <ol>
 *   <li><b>一码管两处</b>：`customer:import` 同时管「导入」与「下载模板」（`:352` 起的同一个三元分支）。
 *       两者必须**同进同出**——只断言其中一个，等于给另一个留了无声断线的余地。</li>
 *   <li><b>一码管三处</b>：`customer:pool_manage` 同时管**行选择**（`:346`）、「公海扫描」（`:365`）、
 *       「批量转移」（`:374`）。三处的判据是**三段独立的代码**，任一处退化都不会被另外两处发现，
 *       所以正负两向都必须三处齐assert。</li>
 *   <li><b>视图判据 ≠ 权限判据</b>：行内「认领」的判据是
 *       `view === 'pool' ? (can[customerClaim] ? «认领» : null) : «编辑/空»`（`:268-278`）——
 *       它**只在公海视图下存在**，与权限无关。于是「有码却看不见」既可能因为没码、也可能因为不在池视图。
 *       第 7/8/9 例把这个二维判据拆成正交的两条轴：同一个持码用户，**切到池视图看得见**（第 7 例）、
 *       **留在全部客户视图看不见**（第 9 例）。缺了第 9 例，池视图那半边的判据就没人守
 *       （把 `view === 'pool'` 写死成 true 不会有任何用例转红）；缺了第 7 例，第 9 例的「看不见」
 *       就分不清是视图造成的还是渲染失败造成的。</li>
 * </ol>
 *
 * <p><b>负向用例必须用非 ADMIN</b>：`hasPerm` 对 `role === 'ADMIN'` 短路返回 true
 * （`hooks/usePermission.ts:8-12`），拿管理员永远测不出「看不见」——那样的负向断言是恒假的假绿。
 * 本文件所有负向主体都是 `role: 'SALES'` 且 `permissions` 显式不含目标码。
 *
 * <p><b>反空洞</b>：每条用例都同时断言一个**恒存在**的元素——ProTable 的 `headerTitle`
 * （`pages.customer.list.title`）与工具栏里**不受任何码管辖**的「导出」（`:362`）。没有这两条锚点，
 * 「按钮不在」与「页面根本没渲染出来」在断言层面完全同形。
 */

/** 表格数据行——行内动作（删除／认领／编辑）必须先有行可渲染，断言才有意义。 */
const customerRow = {
  id: 1,
  name: 'Acme 科技',
  company: 'Acme Inc.',
  contactPerson: '张三',
  phone: '13800000000',
  email: 'zhangsan@acme.com',
  status: 'ACTIVE',
  ownerName: '销售一',
  version: 0,
}

const pageResult = (items: unknown[], total: number) => ({ items, total, page: 1, pageSize: 20 })

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码——所有「看不见」的对照组都建立在它之上。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 逐字同基线，只多一个码：把差异收敛到唯一变量。 */
const salesWithCreate: UserInfo = { ...salesNoPerm, permissions: ['customer:create'] }
const salesWithUpdate: UserInfo = { ...salesNoPerm, permissions: ['customer:update'] }
const salesWithDelete: UserInfo = { ...salesNoPerm, permissions: ['customer:delete'] }
const salesWithImport: UserInfo = { ...salesNoPerm, permissions: ['customer:import'] }
const salesWithPoolManage: UserInfo = { ...salesNoPerm, permissions: ['customer:pool_manage'] }
/** 持「认领」码：第 7/9 两例用它分别证明 池视图×有码 ⇒ 在、非池视图×有码 ⇒ 不在。 */
const salesWithClaim: UserInfo = { ...salesNoPerm, permissions: ['customer:claim'] }
/**
 * 持「认领」+「删除」。第 9 例（非池视图）需要「删除」在场：它证明**数据行与操作列都渲染到位了**，
 * 于是「认领不在」只能归因于视图判据，而不是整列没渲染。第 8 例（池视图，无认领码）反过来用它。
 */
const salesWithClaimAndDelete: UserInfo = {
  ...salesNoPerm,
  permissions: ['customer:claim', 'customer:delete'],
}

/** 恒存在的锚点①：ProTable 的 headerTitle，任何权限组合下都在。 */
const titleCount = () => screen.queryAllByText('pages.customer.list.title').length
/** 恒存在的锚点②：工具栏「导出」——本页唯一不受任何码管辖的工具栏控件（`:362`）。 */
const exportBtn = () => screen.queryByText('pages.customer.list.export')

/** 五个码各自管着的控件（文案在 i18n mock 下就是键字符串）。 */
const createBtn = () => screen.queryByText('pages.customer.list.create')
const importBtn = () => screen.queryByText('pages.customer.list.import')
const templateBtn = () => screen.queryByText('pages.customer.list.downloadTemplate')
const deleteLink = () => screen.queryByText('pages.customer.list.delete')
const claimLink = () => screen.queryByText('pages.customer.list.claim')
const poolScanBtn = () => screen.queryByText('pages.customer.list.poolScan')
const transferBtn = () => screen.queryByText('pages.customer.list.transfer')
/** 同行对照：操作列里**不受本次五个码管辖**的「编辑」（挂 `customer:update`），用来证明该列渲染到了。 */
const editLink = () => screen.queryByText('pages.customer.list.edit')
/** 行选择：有 `rowSelection` 时 antd 会渲染「全选」+ 每行一个 checkbox；没有则一个都没有。 */
const rowCheckboxes = () => screen.queryAllByRole('checkbox')

/** 视图切换按钮——它的 `type` 在 primary/default 间切换，是「视图真的切过去了」的可判定证据。 */
const viewPoolBtn = () => screen.getByRole('button', { name: /pages\.customer\.list\.viewPool/ })

async function renderPage(user: UserInfo) {
  useAuthStore.setState({ user })
  const { fetchCustomers, fetchMyCustomers, fetchPoolCustomers } = await import('../../services/customerService')
  vi.mocked(fetchCustomers).mockResolvedValue(pageResult([customerRow], 1) as never)
  vi.mocked(fetchMyCustomers).mockResolvedValue(pageResult([customerRow], 1) as never)
  vi.mocked(fetchPoolCustomers).mockResolvedValue(pageResult([customerRow], 1) as never)

  renderWithProviders(<CustomerListPage />)
  // 反空洞守卫：等到表格真的渲染出这一行，后续的否定断言才有意义 ——
  // 否则「按钮不在」可能只是「页面还没加载出来」，那样的负向断言是假绿。
  await screen.findByText('Acme 科技')
}

/** 切到公海视图；返回时已断言按钮确实变成了 primary——即 `view === 'pool'` 已生效。 */
async function switchToPoolView() {
  fireEvent.click(viewPoolBtn())
  await waitFor(() => expect(viewPoolBtn()).toHaveClass('ant-btn-primary'))
}

describe('CustomerListPage 五个码的渲染收口（087 补课）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  // ---------------------------------------------------------------- customer:create

  it('持 customer:create 的 SALES 看得见工具栏「新建」（正向）', async () => {
    await renderPage(salesWithCreate)

    expect(titleCount()).toBeGreaterThan(0)
    expect(exportBtn()).toBeInTheDocument()
    expect(createBtn()).toBeInTheDocument()
  })

  it('无 customer:create 的 SALES 看不见「新建」，但工具栏本身照常渲染（负向）', async () => {
    await renderPage(salesNoPerm)

    expect(titleCount()).toBeGreaterThan(0)
    expect(exportBtn()).toBeInTheDocument()
    expect(createBtn()).not.toBeInTheDocument()
  })

  // ---------------------------------------------------------------- customer:delete

  it('持 customer:delete 的 SALES 看得见行内「删除」（正向）', async () => {
    await renderPage(salesWithDelete)

    expect(screen.getByText('Acme 科技')).toBeInTheDocument()
    expect(deleteLink()).toBeInTheDocument()
  })

  it('无 customer:delete 的 SALES 看不见行内「删除」，但同一操作列的「编辑」仍在（负向）', async () => {
    await renderPage(salesWithUpdate)

    // 反空洞守卫：该行确实渲染了，**同一个操作列**也跑到了——消失的只有被收口的那个链接。
    expect(screen.getByText('Acme 科技')).toBeInTheDocument()
    expect(editLink()).toBeInTheDocument()
    expect(deleteLink()).not.toBeInTheDocument()
  })

  // ---------------------------------------------------------------- customer:import（一码管两处）

  it('持 customer:import 的 SALES 看得见「导入」与「下载模板」两个控件（正向，一码管两处）', async () => {
    await renderPage(salesWithImport)

    expect(exportBtn()).toBeInTheDocument()
    expect(importBtn()).toBeInTheDocument()
    expect(templateBtn()).toBeInTheDocument()
  })

  it('无 customer:import 的 SALES 两个控件同缺（负向，一码管两处）', async () => {
    await renderPage(salesNoPerm)

    // 锚点：同一段 toolBarRender 里的「导出」（该分支之外）照常在——工具栏确实渲染过了。
    expect(exportBtn()).toBeInTheDocument()
    expect(importBtn()).not.toBeInTheDocument()
    expect(templateBtn()).not.toBeInTheDocument()
  })

  // ---------------------------------------------------------------- customer:claim（仅池视图 + 视图正交）

  it('持 customer:claim 且切到公海视图的 SALES 看得见行内「领取」（正向）', async () => {
    await renderPage(salesWithClaim)
    await switchToPoolView()

    expect(screen.getByText('Acme 科技')).toBeInTheDocument()
    expect(claimLink()).toBeInTheDocument()
  })

  it('无 customer:claim 的 SALES 在公海视图下看不见「领取」，但同在池视图的行内「删除」仍在（负向）', async () => {
    await renderPage(salesWithDelete)
    await switchToPoolView()

    // 反空洞守卫：视图确实切到了 pool（按钮变 primary）、该行渲染了、同一操作列的「删除」也在
    // ——三者合起来说明消失的只有「领取」这一个受码管辖的链接。
    expect(screen.getByText('Acme 科技')).toBeInTheDocument()
    expect(deleteLink()).toBeInTheDocument()
    expect(claimLink()).not.toBeInTheDocument()
  })

  it('持 customer:claim 但停留在「全部客户」视图：仍然看不见「领取」（视图判据与权限判据正交）', async () => {
    await renderPage(salesWithClaimAndDelete)

    // 视图守卫：未切池视图（按钮不是 primary）。持码 + 非池视图 ⇒ 不在。
    // 这条不是第 7 例的冗余：它守的是 `view === 'pool'` 那半边判据——把该判据写死成 true，
    // 只有本条会转红（第 7/8 例都测不出）。
    expect(viewPoolBtn()).not.toHaveClass('ant-btn-primary')
    expect(screen.getByText('Acme 科技')).toBeInTheDocument()
    expect(deleteLink()).toBeInTheDocument()
    expect(claimLink()).not.toBeInTheDocument()
  })

  // ---------------------------------------------------------------- customer:pool_manage（一码管三处）

  it('持 customer:pool_manage 的 SALES：行选择、「公海扫描」、「批量转移」三处同现（正向）', async () => {
    await renderPage(salesWithPoolManage)

    expect(titleCount()).toBeGreaterThan(0)
    expect(exportBtn()).toBeInTheDocument()
    expect(rowCheckboxes().length).toBeGreaterThan(0)
    expect(poolScanBtn()).toBeInTheDocument()
    expect(transferBtn()).toBeInTheDocument()
  })

  it('无 customer:pool_manage 的 SALES：行选择、「公海扫描」、「批量转移」三处同缺（负向）', async () => {
    await renderPage(salesNoPerm)

    expect(titleCount()).toBeGreaterThan(0)
    expect(exportBtn()).toBeInTheDocument()
    expect(screen.getByText('Acme 科技')).toBeInTheDocument()
    // 三处判据是三段独立的代码，任一处退化都不会被另外两处发现 —— 断言必须三处齐。
    expect(rowCheckboxes()).toHaveLength(0)
    expect(poolScanBtn()).not.toBeInTheDocument()
    expect(transferBtn()).not.toBeInTheDocument()
  })

  // ---------------------------------------------------------------- 互不串门

  it('互不串门：只授 customer:create 时，其余四个码管着的控件一处都不出现', async () => {
    await renderPage(salesWithCreate)

    expect(titleCount()).toBeGreaterThan(0)
    expect(exportBtn()).toBeInTheDocument()
    expect(createBtn()).toBeInTheDocument()

    expect(importBtn()).not.toBeInTheDocument()
    expect(templateBtn()).not.toBeInTheDocument()
    expect(deleteLink()).not.toBeInTheDocument()
    expect(claimLink()).not.toBeInTheDocument()
    expect(poolScanBtn()).not.toBeInTheDocument()
    expect(transferBtn()).not.toBeInTheDocument()
    expect(rowCheckboxes()).toHaveLength(0)
  })

  // ---------------------------------------------------------------- ADMIN 语义保留

  it('ADMIN 看得见全部受码管辖的控件（既有短路语义未被本次收口误伤）', async () => {
    await renderPage(adminUser)

    expect(createBtn()).toBeInTheDocument()
    expect(importBtn()).toBeInTheDocument()
    expect(templateBtn()).toBeInTheDocument()
    expect(deleteLink()).toBeInTheDocument()
    expect(rowCheckboxes().length).toBeGreaterThan(0)
    expect(poolScanBtn()).toBeInTheDocument()
    expect(transferBtn()).toBeInTheDocument()
  })
})
