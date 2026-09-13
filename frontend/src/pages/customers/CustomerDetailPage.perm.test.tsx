import { describe, expect, it, vi, beforeEach } from 'vitest'
import { Route, Routes } from 'react-router-dom'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import CustomerDetailPage from './CustomerDetailPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'
import { useCustomerDetail } from '../../hooks/useCustomers'

vi.mock('../../services/customerService', () => ({
  fetchCustomer: vi.fn(),
  fetchCustomers: vi.fn(),
  fetchAtRiskCustomers: vi.fn(),
}))
vi.mock('../../services/followUpService', () => ({
  fetchFollowUps: vi.fn(async () => ({ items: [], total: 0, page: 1, pageSize: 50 })),
}))
vi.mock('../../services/contactService', () => ({
  fetchContacts: vi.fn(async () => ({ items: [], total: 0, page: 1, pageSize: 50 })),
}))
vi.mock('../../services/customerShareService', () => ({
  shareCustomer: vi.fn(),
}))
vi.mock('../../services/userService', () => ({
  fetchUsers: vi.fn(async () => ({ items: [], total: 0, page: 1, pageSize: 100 })),
}))
vi.mock('../../hooks/useCustomers', () => ({
  useCustomerDetail: vi.fn(),
}))

/**
 * 086 权限收口 · `CustomerDetailPage`「共享」按钮的**两闸门**测试。
 *
 * <p>本页的判据是全批里唯一**不能用 `hasPerm` 单独表达**的一处，所以它值得一个四格矩阵
 * （`CustomerDetailPage.tsx:61-72` / `:189-190`）：
 *
 * <pre>
 *   canShare = !!data && hasPerm(customer:update) && (isAdmin || ownerId === user.id)
 *                      └────── ① 码闸门 ──────┘   └──── ② 数据归属闸门（Admin 例外） ────┘
 * </pre>
 *
 * <p>后端是**两道真的闸门**，前端必须逐字对上：
 * ① `CustomerShareController.java:52,60` 的 `@RequirePermission("customer:update")`；
 * ② `CustomerShareService.share` 的 `if (!isAdmin && !ownerId.equals(currentUserId)) throw FORBIDDEN`。
 *
 * <p>为什么 ② 里的 `isAdmin` **不能**像别处那样删掉换成 `hasPerm`：`hasPerm` 把"是 ADMIN"与
 * "持有该码"合并成了同一个 true（`hooks/usePermission.ts:10`），于是表达不出"是 ADMIN 但**不是** owner"
 * 这一支——而后端恰恰允许管理员共享任何人的客户。把它换成 `hasPerm(customer:update)` 会让
 * **非 owner 的管理员丢掉共享按钮**（因为 `ownerId === user.id` 为假），这就是本文件第 1 例守的东西。
 *
 * <p>反向的 ③ 同样要有：改造前这里**完全没有码闸门**，一个拥有客户却没有 `customer:update`
 * 的角色会看见「共享」却必然 403——第 3 例守的是这个（用非 ADMIN 才测得出）。
 */
const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码。负向用例必须建立在它之上（ADMIN 在 hasPerm 里直通，永远拿不到"看不见"）。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
const salesWithPerm: UserInfo = { ...salesNoPerm, permissions: ['customer:update'] }

const makeDetail = (ownerId: number) => ({
  id: 1,
  name: 'Acme 科技',
  company: 'Acme Inc.',
  status: 'ACTIVE',
  version: 0,
  ownerId,
  ownerName: '某负责人',
  opportunities: [],
  followUps: [],
  contacts: [],
})

const shareButton = () => screen.queryByRole('button', { name: /common\.button\.share/ })

async function renderPage(user: UserInfo, ownerId: number) {
  useAuthStore.setState({ user })
  vi.mocked(useCustomerDetail).mockReturnValue({ data: makeDetail(ownerId), isLoading: false, error: null } as never)

  renderWithProviders(
    <Routes>
      <Route path="/customers/:id" element={<CustomerDetailPage />} />
    </Routes>,
    { route: '/customers/1' },
  )
  // 等到页面真的渲染出客户名，后续的否定断言才有意义 ——
  // 否则"按钮不在"可能只是"页面还没渲染完"，那样的负向断言是假绿。
  // 用 findAll：客户名在面包屑/标题/卡片里出现多次（018 的 render 测试也是这么等的）。
  await screen.findAllByText('Acme 科技', {}, { timeout: 5000 })
}

describe('CustomerDetailPage 共享按钮的两闸门（086）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('① 管理员共享**他人**的客户：可见（② 的 Admin 例外）', async () => {
    await renderPage(adminUser, 99) // 99 ≠ admin 的 id=1，即"是 ADMIN 但不是 owner"

    expect(shareButton()).toBeInTheDocument()
  })

  it('② 持有 customer:update 且是归属者的 SALES：可见（正当路径）', async () => {
    await renderPage(salesWithPerm, 2) // ownerId 2 === 该用户的 id

    expect(shareButton()).toBeInTheDocument()
  })

  it('③ 是归属者但**无** customer:update 的 SALES：不可见（改造前会露出必然 403 的按钮）', async () => {
    await renderPage(salesNoPerm, 2)

    expect(screen.getAllByText('Acme 科技').length).toBeGreaterThan(0) // 页面确实渲染好了
    expect(shareButton()).not.toBeInTheDocument()
  })

  it('④ 持有 customer:update 但**不是**归属者的 SALES：不可见（② 的归属规则保留）', async () => {
    await renderPage(salesWithPerm, 99)

    expect(screen.getAllByText('Acme 科技').length).toBeGreaterThan(0)
    expect(shareButton()).not.toBeInTheDocument()
  })
})
