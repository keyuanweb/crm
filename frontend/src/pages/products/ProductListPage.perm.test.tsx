import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import ProductListPage from './ProductListPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/productService', () => ({
  fetchProducts: vi.fn(),
  createProduct: vi.fn(),
  updateProduct: vi.fn(),
  deleteProduct: vi.fn(),
}))
// 页面只在**打开弹窗**时才碰多币种接口（057），渲染期不会调用；mock 掉是为了不让
// 真实的 apiClient 进到模块图里——一旦哪天有人在渲染路径上加了调用，这里会立刻显形。
vi.mock('../../services/currencyService', () => ({
  fetchCurrencies: vi.fn(async () => []),
  createCurrency: vi.fn(),
  updateCurrency: vi.fn(),
  deleteCurrency: vi.fn(),
  convertAmount: vi.fn(),
  fetchProductPrices: vi.fn(),
  setProductPrice: vi.fn(),
  deleteProductPrice: vi.fn(),
}))

/**
 * 087 补课 · `ProductListPage` 的**三码互不串门**渲染测试。
 *
 * <p>产品页的三个按钮挂的是三个**不同**的码（映射表见 `specs/087-perm-test-backfill/plan.md`）：
 * <pre>
 *   工具栏「新增产品」 ← product:create   （ProductListPage.tsx:232）
 *   行内「编辑」      ← product:update   （:191）
 *   行内「删除」      ← product:delete   （:196）
 * </pre>
 *
 * <p><b>为什么必须逐个码各测一遍</b>：这三个码的**授予范围本就不同**——create / delete 只给
 * ADMIN + MARKETING_MANAGER，update 另有销售角色（V83 授出，页面注释 `:61-63` 写明了）。
 * 所以「销售看得到「编辑」却看不到「新建」「删除」」是**权限矩阵的正确答案**，
 * 不是界面漏改。只测其中一个码就推定另两个，会把这条矩阵语义整个略过。
 *
 * <p>因此每条**正向**用例除了断言"自己的按钮在"，还要断言**另两个码的按钮都不在**——
 * 那就是"互不串门"的锁：哪天有人把三处判据复制粘贴成同一个码，这两条断言立刻红。
 *
 * <p>负向用例**必须**用非 ADMIN：`hasPerm` 对 `role === 'ADMIN'` 直通返回 true
 * （`hooks/usePermission.ts:10`），拿管理员永远测不出「看不见」。
 */
const productRow = {
  id: 1,
  code: 'CRM-STD',
  name: '标准产品',
  spec: '个',
  unit: '个',
  standardPrice: 10000,
  status: 'ACTIVE',
  version: 0,
}

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码——"看不见"的对照组必须建立在它之上。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 以下三个用户各只多一个码，其余与基线逐字相同——把差异收敛到唯一变量。 */
const salesWithCreate: UserInfo = { ...salesNoPerm, permissions: ['product:create'] }
const salesWithUpdate: UserInfo = { ...salesNoPerm, permissions: ['product:update'] }
const salesWithDelete: UserInfo = { ...salesNoPerm, permissions: ['product:delete'] }

/** 工具栏「新建」按钮（`product:create`）。 */
const queryCreate = () => screen.queryByText('pages.product.list.create')
/** 行内「编辑」链接（`product:update`）。 */
const queryEdit = () => screen.queryByText('pages.product.list.edit')
/** 行内「删除」链接（`product:delete`）。 */
const queryDelete = () => screen.queryByText('pages.product.list.delete')

async function renderPage(user: UserInfo) {
  useAuthStore.setState({ user })
  const { fetchProducts } = await import('../../services/productService')
  vi.mocked(fetchProducts).mockResolvedValue({ items: [productRow], total: 1, page: 1, pageSize: 20 } as never)

  renderWithProviders(<ProductListPage />)
  // 反空洞守卫：等到表格真的渲染出这一行，后续的否定断言才有意义 ——
  // 否则"按钮不在"可能只是"页面还没加载出来"，那样的负向断言是假绿。
  await screen.findByText(productRow.name)
}

/**
 * 反空洞守卫：标题、编码列、名称列都在 ⇒ 页面与数据行确实渲染过了。
 * 这是**非空性**证明（FR-004 允许它当锚点），但**不得**用它充当"有权限"的证据。
 */
function expectPageRendered() {
  expect(screen.getByText('pages.product.list.title')).toBeInTheDocument()
  expect(screen.getByText(productRow.code)).toBeInTheDocument()
  expect(screen.getByText(productRow.name)).toBeInTheDocument()
}

describe('ProductListPage create/update/delete 三码的渲染收口（087）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('ADMIN 看得见「新建」「编辑」「删除」（三码对管理员直通）', async () => {
    await renderPage(adminUser)

    expectPageRendered()
    expect(queryCreate()).toBeInTheDocument()
    expect(queryEdit()).toBeInTheDocument()
    expect(queryDelete()).toBeInTheDocument()
  })

  // ---------------------------------------------------------------- product:create

  it('持有 product:create 的 SALES 看得见「新建」；「编辑」「删除」都不在（三码互不串门）', async () => {
    await renderPage(salesWithCreate)

    expectPageRendered()
    expect(queryCreate()).toBeInTheDocument()
    expect(queryEdit()).not.toBeInTheDocument()
    expect(queryDelete()).not.toBeInTheDocument()
  })

  it('无 product:create 的 SALES 看不见工具栏「新建」（页面与数据行照常渲染）', async () => {
    await renderPage(salesNoPerm)

    expectPageRendered()
    expect(queryCreate()).not.toBeInTheDocument()
  })

  // ---------------------------------------------------------------- product:update

  it('持有 product:update 的 SALES 看得见行内「编辑」；「新建」「删除」都不在（销售可改但建不了删不了）', async () => {
    await renderPage(salesWithUpdate)

    expectPageRendered()
    // 同行对照：同一 action 列里**不受权限管辖**的证据由数据行本身提供（本列两个控件都按码收，
    // 故用"行在 + 另两个码的控件都不在"共同钉住这一处判据落点）。
    expect(queryEdit()).toBeInTheDocument()
    expect(queryCreate()).not.toBeInTheDocument()
    expect(queryDelete()).not.toBeInTheDocument()
  })

  it('无 product:update 的 SALES 看不见行内「编辑」（页面与数据行照常渲染）', async () => {
    await renderPage(salesNoPerm)

    expectPageRendered()
    expect(queryEdit()).not.toBeInTheDocument()
  })

  // ---------------------------------------------------------------- product:delete

  it('持有 product:delete 的 SALES 看得见行内「删除」；「编辑」「新建」都不在（三码互不串门）', async () => {
    await renderPage(salesWithDelete)

    expectPageRendered()
    expect(queryDelete()).toBeInTheDocument()
    expect(queryEdit()).not.toBeInTheDocument()
    expect(queryCreate()).not.toBeInTheDocument()
  })

  it('无 product:delete 的 SALES 看不见行内「删除」（页面与数据行照常渲染）', async () => {
    await renderPage(salesNoPerm)

    expectPageRendered()
    expect(queryDelete()).not.toBeInTheDocument()
  })
})
