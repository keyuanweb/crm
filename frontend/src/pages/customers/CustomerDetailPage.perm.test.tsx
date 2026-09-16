import { describe, expect, it, vi, beforeEach } from 'vitest'
import { Route, Routes } from 'react-router-dom'
import { screen, within } from '@testing-library/react'
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
vi.mock('../../services/commentService', () => ({
  fetchComments: vi.fn(),
  createComment: vi.fn(),
  deleteComment: vi.fn(),
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

/**
 * 096 权限收口 · `CommentSection`（渲染在客户详情页里）的**两个码 ∧ 归属**测试。
 *
 * <p>本组件 096 之前是**整页零判权**：发布输入区无条件渲染、删除链接只判
 * `role === 'ADMIN' || 作者本人`。后端当时是**类级** `@PreAuthorize("hasAnyRole('ADMIN','SALES','SUPPORT')")`
 * ⇒ 非这三者的用户看得见按钮、点下去必然 403。096 建了 `comment:create` / `comment:delete`
 * 两码并撤掉类级门（补授范围 = 那道门原先放行的集合，逐字不变），本文件据此钉住三件事：
 *
 * <ol>
 *   <li>两个码**各自独立**（第 5 例：只持 delete 的人没有发布区）；
 *   <li>删除的判据是 **码 ∧ 归属**，那半个 ADMIN 例外不能删——第 1 例（是 ADMIN 但不是作者）
 *       与第 3 例（有码但不是作者）合起来把它钉死：删掉例外则第 1 例红，只留归属则第 4 例红
 *       （第 4 例的作者正是本人，无码时**改造前看得见**删除按钮）；
 *   <li>负向用例用非 ADMIN：`hasPerm` 对 ADMIN 直通，拿管理员测不出「看不见」。
 * </ol>
 */
const otherComment = {
  id: 11,
  entityType: 'CUSTOMER',
  entityId: 1,
  content: '别家的留言',
  authorId: 99, // 非本人
  authorName: '别人',
  createdAt: '2026-09-16T09:00:00',
}
const myComment = { ...otherComment, id: 12, content: '我自己的留言', authorId: 2, authorName: '销售一' }

const salesComments = (codes: string[]): UserInfo => ({ ...salesNoPerm, permissions: codes })
const submitButton = () => screen.queryByText('pages.commentSection.btnSubmit')
const deleteLink = () => screen.queryByText('pages.commentSection.btnDelete')

/**
 * 在某条评论**所在的那一行**里找控件。
 *
 * <p>CommentSection 的一条评论是 `<div><Space>头像/作者/时间/删除</Space><div>正文</div></div>`
 * （`CommentSection.tsx`），删除链接与正文同父。用行级断言而不是「页面上有几个删除链接」，
 * 是为了让断言说的是**哪一条评论**可删——否则「恰好剩 1 个」既可能是「他人的不可删」，
 * 也可能是「本人的那个没渲染」，两种情形分不开。
 */
function inRowOf(content: string, text: string) {
  const row = screen.getByText(content).parentElement as HTMLElement
  return within(row).queryByText(text)
}

/** 评论区用例：`useCustomerDetail` 给一条客户，评论取数给**一条本人 + 一条他人**。 */
async function renderComments(user: UserInfo) {
  useAuthStore.setState({ user })
  const { fetchComments } = await import('../../services/commentService')
  vi.mocked(fetchComments).mockResolvedValue({
    items: [myComment, otherComment],
    total: 2,
    page: 1,
    pageSize: 50,
  } as never)

  await renderPage(user, 2) // ownerId 2 === 本人，排除「共享」按钮的干扰
  // 反空洞守卫：两条评论都渲染出来了，后面的否定断言才有意义。
  await screen.findByText('我自己的留言')
  await screen.findByText('别家的留言')
}

describe('CommentSection 发布与删除的两个码（096）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('① ADMIN：发布区可见，且**他人**的评论也能删（数据归属的 ADMIN 例外）', async () => {
    await renderComments(adminUser)

    expect(submitButton()).toBeInTheDocument()
    // 逐行断言：本人与他人的那两条**各自**都有删除链接
    expect(inRowOf('我自己的留言', 'pages.commentSection.btnDelete')).toBeInTheDocument()
    expect(inRowOf('别家的留言', 'pages.commentSection.btnDelete')).toBeInTheDocument()
  })

  it('② 持 comment:create + comment:delete 且是评论作者：发布区可见，自己的那条可删', async () => {
    await renderComments(salesComments(['comment:create', 'comment:delete']))

    expect(submitButton()).toBeInTheDocument()
    expect(deleteLink()).toBeInTheDocument()
    expect(inRowOf('我自己的留言', 'pages.commentSection.btnDelete')).toBeInTheDocument()
  })

  it('③ 有码但**不是**作者：他人的那条不可删（归属闸门保留）', async () => {
    await renderComments(salesComments(['comment:create', 'comment:delete']))

    // 逐行断言把「谁的评论」钉死：不是"页面上只剩一个删除链接"这种间接证据。
    expect(inRowOf('我自己的留言', 'pages.commentSection.btnDelete')).toBeInTheDocument()
    expect(inRowOf('别家的留言', 'pages.commentSection.btnDelete')).not.toBeInTheDocument()
  })

  it('④ **无码**但是作者：发布区与删除都不可见（改造前作者看得见这个必然 403 的删除）', async () => {
    await renderComments(salesNoPerm)

    expect(screen.getByText('我自己的留言')).toBeInTheDocument()
    expect(submitButton()).not.toBeInTheDocument()
    expect(deleteLink()).not.toBeInTheDocument()
  })

  it('⑤ 只持 comment:delete（是作者）：删除可见、发布区不可见（两码各自独立）', async () => {
    await renderComments(salesComments(['comment:delete']))

    expect(deleteLink()).toBeInTheDocument()
    expect(submitButton()).not.toBeInTheDocument()
  })
})
