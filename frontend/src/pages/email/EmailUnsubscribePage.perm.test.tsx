import { describe, expect, it, vi, beforeEach } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import EmailUnsubscribePage from './EmailUnsubscribePage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/emailService', () => ({
  fetchUnsubscribes: vi.fn(),
  restoreUnsubscribe: vi.fn(),
}))

/**
 * 087 权限用例回填 · `EmailUnsubscribePage` 行内「恢复」的双向渲染测试。
 *
 * <p><b>控件 → 判据 → 码</b>：动作列（`colAction`）里那一格唯一的控件——`Popconfirm` 包着的
 * 「恢复」链接——由 `can[PERMS.emailManage]` 决定是否渲染（`EmailUnsubscribePage.tsx:41`），
 * `PERMS.emailManage === 'email:manage'`（`constants/permissions.ts:177`）。后端对得上：
 * `EmailController` 全线只有 `email:manage` 一个写码，退订恢复走
 * `DELETE /email/unsubscribes/{id}`；service 侧 `restoreUnsubscribe` 正是这条路径
 * （`services/emailService.ts:92-94`）。
 *
 * <p><b>本文件为何必须存在</b>：086 统计「哪些码已有渲染用例」时按**码字面量出现与否**计数，
 * 而 `email:manage` 恰好作为**别页的负向夹具**出现在
 * `pages/landing/LandingPageListPage.perm.test.tsx:104`（那里断言的是"持 `email:manage` 也删不了
 * 落地页"），于是本码被误判为"已覆盖"。字面量出现 ≠ 本页判据被执行：`EmailUnsubscribePage` 的
 * 那一行在 087 之前从未被任何用例执行过。第 4 例把 `campaign:delete` 单独喂进去，正是要把
 * "同属邮件/营销域但各自独立的码不可互替"钉住。
 *
 * <p><b>唯一变量</b>：三个用户对象只差 `permissions` 一项；行数据与页面其余部分逐字相同，
 * 所以"「恢复」在/不在"只可能来自判据。
 *
 * <p>负向用例**必须**用非 ADMIN：`hasPerm` 对 `role === 'ADMIN'` 直接短路返回 true
 * （`hooks/usePermission.ts:10`），管理员永远测不出"看不见"，拿 ADMIN 跑负向是恒真的假绿。
 *
 * <p><b>如实说明</b>：本页拉列表的 `GET /email/unsubscribes` 挂的**也是** `email:manage`
 * （`EmailController.java:127-128`），生产环境里非持有者连名单都拉不出来（403），真实数据行
 * 不会出现。本文件把 `fetchUnsubscribes` 替身化，才能在"无码"下渲染出数据行，把「恢复不见了」
 * 与「页面没渲染」分开——反空洞守卫就是这个用途。第 ②/③ 例锁的是**判据的正确性**，不是
 * 生产里的可观测差异。
 */
const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码——"看不见"的对照组必须建立在它之上。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 只多了 `email:manage`，其余与基线逐字相同——把差异收敛到唯一变量。 */
const salesWithPerm: UserInfo = { ...salesNoPerm, permissions: ['email:manage'] }

const row = {
  id: 9,
  email: 'unsub@example.com',
  campaignId: 7,
  unsubscribedAt: '2026-01-02T03:04:05',
}

const queryRestore = () => screen.queryAllByText('pages.emailUnsubscribe.btnRestore')

async function renderPage(user: UserInfo) {
  useAuthStore.setState({ user })
  const { fetchUnsubscribes } = await import('../../services/emailService')
  vi.mocked(fetchUnsubscribes).mockResolvedValue({ items: [row], total: 1, page: 1, pageSize: 20 } as never)
  renderWithProviders(<EmailUnsubscribePage />)
  // 反空洞守卫：等到这一行真的渲染出来，后面的否定断言才有意义 ——
  // 否则"「恢复」不在"可能只是"表格还没加载出来"（渲染失败被读成"正确地不在"）。
  await screen.findByText('unsub@example.com', {}, { timeout: 5000 })
}

/**
 * 反空洞守卫：页面确实渲染过，且逐条钉住**不受 `email:manage` 管辖**的相邻内容。
 *
 * <p>`colEmail` 同时是搜索表单的标签与列头（该列未设 `search: false`），所以它天然出现两次，
 * 只能按数量断言——`getByText` 在多元素匹配时**抛错**。
 */
function expectPageRendered() {
  // 恒存在的锚点：页标题
  expect(screen.getByText('pages.emailUnsubscribe.title')).toBeInTheDocument()
  // 行数据（来自被 mock 的接口，本页无业务状态前置条件，行姿态在三种用户下完全相同）
  expect(screen.getByText('unsub@example.com')).toBeInTheDocument()
  expect(screen.getByText('#7')).toBeInTheDocument()
  expect(screen.getByText('2026-01-02 03:04:05')).toBeInTheDocument()
  // 三个列头，与权限无关
  expect(screen.queryAllByText('pages.emailUnsubscribe.colEmail').length).toBeGreaterThan(0)
  expect(screen.getByText('pages.emailUnsubscribe.colCampaign')).toBeInTheDocument()
  expect(screen.getByText('pages.emailUnsubscribe.colUnsubscribedAt')).toBeInTheDocument()
  expect(screen.getByText('pages.emailUnsubscribe.colAction')).toBeInTheDocument()
}

describe('EmailUnsubscribePage 行内「恢复」的权限收口（087）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('① 持 email:manage 的 SALES：行内「恢复」可见', async () => {
    await renderPage(salesWithPerm)

    expectPageRendered()
    expect(queryRestore()).toHaveLength(1)
  })

  it('② 零权限码的 SALES：页面照常渲染，唯独「恢复」不可见', async () => {
    await renderPage(salesNoPerm)

    expectPageRendered()
    expect(queryRestore()).toHaveLength(0)
  })

  it('③ 只持同域但独立的 campaign:delete 不构成放行——「恢复」仍旧不可见', async () => {
    await renderPage({ ...salesNoPerm, permissions: ['campaign:delete'] })

    expectPageRendered()
    expect(queryRestore()).toHaveLength(0)
  })

  it('④ ADMIN：「恢复」可见（ADMIN 由 hasPerm 直通，与码无关，只作正向锚点）', async () => {
    await renderPage(adminUser)

    expectPageRendered()
    expect(queryRestore()).toHaveLength(1)
  })

  it('⑤ 有码时「恢复」确实接到恢复端点：确认后以该行 id 调用 restoreUnsubscribe', async () => {
    const { restoreUnsubscribe } = await import('../../services/emailService')
    vi.mocked(restoreUnsubscribe).mockResolvedValue(undefined)
    await renderPage(salesWithPerm)

    fireEvent.click(queryRestore()[0])
    // 确认气泡出现，说明这个链接确实是「恢复」动作（而不是只剩个装饰性的字）
    expect(
      await screen.findByText(/pages\.emailUnsubscribe\.confirmRestore/, {}, { timeout: 5000 }),
    ).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /确\s*定/ }))

    await waitFor(() => expect(restoreUnsubscribe).toHaveBeenCalledWith(row.id))
  })
})
