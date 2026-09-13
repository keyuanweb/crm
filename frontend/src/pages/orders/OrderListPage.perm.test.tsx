import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import OrderListPage from './OrderListPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/orderService', () => ({
  fetchOrders: vi.fn(),
  createOrder: vi.fn(),
  deleteOrder: vi.fn(),
}))
vi.mock('../../services/customerService', () => ({
  fetchCustomers: vi.fn(async () => ({ items: [], total: 0, page: 1, pageSize: 20 })),
}))
vi.mock('../../services/contractService', () => ({
  fetchContracts: vi.fn(async () => ({ items: [], total: 0, page: 1, pageSize: 20 })),
}))

/**
 * 087 追补 · `OrderListPage` 行内「删除」的渲染测试（`order:delete`）。
 *
 * <p><b>这个码是"早已接线、但零渲染层用例"的 15 个码之一</b>（086 收尾盘点 T103 列出的）。
 * 判据在 `OrderListPage.tsx:65`：`const canDelete = hasPerm(PERMS.orderDelete, user)`，
 * 落点是 `:211` 的 `canDelete ? [<Popconfirm>…删除…</Popconfirm>] : []`——**整个操作列的内容**
 * 都由它决定（不是"按钮藏起来、列还在"，而是该行操作列渲染成空数组）。
 * 删除订单打的是 `DELETE /api/v1/orders/{id}`，`OrderController.delete` 上标的就是 `order:delete`。
 *
 * <p><b>为什么必须双向成对</b>：只测"无码 ⇒ 看不见"会放过"判据挂到死码上"（按钮对所有人消失）；
 * 只测"有码 ⇒ 看得见"会放过"判据恒真"。两条断言合起来才锁定"这个码真的在管这个按钮"。
 *
 * <p><b>锚点防真空</b>：每条用例都同时断言页面标题与数据行本身在文档里——否则整页渲染失败会被
 * 误判成"按钮正确地不在"，那种负向断言是假绿。工具栏的「新建」按 086 决策 1（新建不收口）
 * 始终渲染，是第三个恒存在锚点。
 *
 * <p>负向用例**必须**用非 ADMIN：`hasPerm` 对 `role === 'ADMIN'` 直通返回 true
 * （`hooks/usePermission.ts:10`），拿管理员永远测不出「看不见」。
 */
const orderRow = {
  id: 1,
  orderNo: 'SO-2026-0001',
  title: '订单甲',
  customerId: 7,
  customerName: 'Acme 科技',
  amount: 100000,
  status: 'PENDING',
  paidAmount: 0,
  version: 0,
}

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码——"看不见"的对照组必须建立在它之上。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 只多了 `order:delete`，其余与基线逐字相同——把差异收敛到唯一变量。 */
const salesWithPerm: UserInfo = { ...salesNoPerm, permissions: ['order:delete'] }

/** 行内「删除」链接（`DeleteOutlined` 是 svg 不含文本，故 `<a>` 的可见文本恰是这个键名）。 */
const queryDelete = () => screen.queryByText('pages.order.list.delete')

async function renderPage(user: UserInfo) {
  useAuthStore.setState({ user })
  const { fetchOrders } = await import('../../services/orderService')
  vi.mocked(fetchOrders).mockResolvedValue({ items: [orderRow], total: 1, page: 1, pageSize: 20 } as never)

  renderWithProviders(<OrderListPage />, { route: '/orders' })
  // 反空洞守卫：等到表格真的渲染出这一行，后续的否定断言才有意义 ——
  // 否则"按钮不在"可能只是"页面还没加载出来"，那样的负向断言是假绿。
  await screen.findByText('订单甲')
}

describe('OrderListPage 行内删除的权限接线（087 T016 · order:delete）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('① ADMIN 看得见行内「删除」（锚点：标题、数据行、工具栏「新建」都在）', async () => {
    await renderPage(adminUser)

    expect(screen.getByText('pages.order.list.title')).toBeInTheDocument()
    expect(screen.getByText('订单甲')).toBeInTheDocument()
    expect(screen.getByText('pages.order.list.create')).toBeInTheDocument()
    expect(queryDelete()).toBeInTheDocument()
  })

  it('② 无 order:delete 的 SALES 看不见「删除」（负向；页面与数据行照常渲染）', async () => {
    await renderPage(salesNoPerm)

    // 反空洞守卫：页面标题、数据行、工具栏「新建」都在——证明渲染已完成且没有整页崩掉，
    // 不见的确实只是那个受权限管辖的控件。
    expect(screen.getByText('pages.order.list.title')).toBeInTheDocument()
    expect(screen.getByText('订单甲')).toBeInTheDocument()
    expect(screen.getByText('pages.order.list.create')).toBeInTheDocument()
    expect(queryDelete()).not.toBeInTheDocument()
  })

  it('③ 持有 order:delete 的 SALES 看得见「删除」（正向；锁住码的身份）', async () => {
    await renderPage(salesWithPerm)

    expect(screen.getByText('订单甲')).toBeInTheDocument()
    expect(queryDelete()).toBeInTheDocument()
  })
})
