import { describe, expect, it, vi, beforeEach } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import LandingPageListPage from './LandingPageListPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'
import type { LandingPage } from '../../types/landingPage'

vi.mock('../../services/landingPageService', () => ({
  createLandingPage: vi.fn(),
  deleteLandingPage: vi.fn(),
  fetchLandingPages: vi.fn(),
  updateLandingPage: vi.fn(),
}))
// 弹窗打开时才会调它（`openCreate` / `openEdit` 里的 loadForms）；本文件不开弹窗，
// 但必须替身化，否则模块会真去建 axios 客户端。
vi.mock('../../services/formService', () => ({ fetchForms: vi.fn() }))

/**
 * 086 权限收口 · `LandingPageListPage` 行内「删除」的双向渲染测试。
 *
 * <p><b>控件 → 判据 → 码</b>：行内动作列的第三格（`Popconfirm` 包着的红色「删除」链接）由
 * `can[PERMS.marketingManage]` 决定是否渲染，`PERMS.marketingManage === 'marketing:manage'`
 * （`constants/permissions.ts:204`）。后端对得上：`LandingPageController.java:69-70` 的
 * `@DeleteMapping("/landing-pages/{id}")` 挂的正是 `marketing:manage`；
 * service 侧 `deleteLandingPage` → `DELETE /landing-pages/{id}`（`services/landingPageService.ts:25-27`）。
 *
 * <p><b>为什么码选得对（而不是随手挑了个营销码）</b>：`EmailController` 全线的写码是
 * `email:manage`、`MarketingController` 的活动删除是 `campaign:delete`——本页**只认**
 * `marketing:manage`。第 4 例拿 `email:manage` 做交叉否定，正好锁住"这些同属营销域但各自独立的码
 * 不可互替"这一点。
 *
 * <p><b>如实说明</b>：`GET /landing-pages`（`LandingPageController.java:43-44`）挂的**也是**
 * `marketing:manage`。所以生产环境里非持有者连列表都拉不出来（403），真实数据行不会出现；
 * 本文件把 `fetchLandingPages` 替身化，才能在"无码"下渲染出数据行，把「删除不见了」与
 * 「页面没渲染」分开——第 2 例的反空洞守卫就是这个用途。第 2 例锁的是**判据的正确性**，
 * 不是生产里的可观测差异。
 *
 * <p>负向用例**必须**用非 ADMIN：`hasPerm` 对 `role === 'ADMIN'` 直通返回 true
 * （`hooks/usePermission.ts:10`），管理员永远测不出「看不见」。
 */
const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码——"看不见"的对照组必须建立在它之上。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 只多了 `marketing:manage`，其余与基线逐字相同——把差异收敛到唯一变量。 */
const salesWithManage: UserInfo = { ...salesNoPerm, permissions: ['marketing:manage'] }

const row: LandingPage = {
  id: 5,
  title: '双十一活动落地页',
  subtitle: '限时八折',
  formName: '线索表单',
  formId: 2,
  enabled: true,
  version: 3,
}

const queryDelete = () => screen.queryByText('pages.landing.btnDelete')

async function renderPage(user: UserInfo) {
  useAuthStore.setState({ user })
  const { fetchLandingPages } = await import('../../services/landingPageService')
  vi.mocked(fetchLandingPages).mockResolvedValue({ items: [row], total: 1, page: 1, pageSize: 20 } as never)
  renderWithProviders(<LandingPageListPage />)
  // 反空洞守卫：标题来自被 mock 的接口数据，出现即证明表格 request 已跑通、行已渲染。
  await screen.findByText('双十一活动落地页', {}, { timeout: 5000 })
}

describe('LandingPageListPage 行内「删除」的权限收口（086）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('① ADMIN：行内「删除」可见（ADMIN 由 hasPerm 直通，与码无关）', async () => {
    await renderPage(adminUser)

    expect(queryDelete()).toBeInTheDocument()
  })

  it('② 零权限码的 SALES：页面真实内容照常渲染，唯独「删除」不可见', async () => {
    await renderPage(salesNoPerm)

    // 反空洞守卫：把与权限无关的真实内容逐条钉住，否则下面的"删除不在"可能只是整页没渲染
    expect(screen.getByText('双十一活动落地页')).toBeInTheDocument()
    expect(screen.getByText('限时八折')).toBeInTheDocument()
    expect(screen.getByText('线索表单')).toBeInTheDocument()
    expect(screen.getByText('pages.landing.statusActive')).toBeInTheDocument()
    // 工具栏「新建落地页」与行内「统计」「编辑」刻意未收口（086 只收破坏性动作）
    expect(screen.getByRole('button', { name: /pages\.landing\.btnCreate/ })).toBeInTheDocument()
    expect(screen.getByText('pages.landing.btnStats')).toBeInTheDocument()
    expect(screen.getByText('pages.landing.btnEdit')).toBeInTheDocument()

    expect(queryDelete()).not.toBeInTheDocument()
  })

  it('③ 持 marketing:manage 的 SALES：「删除」可见（锁住码的身份，非 ADMIN 亦可）', async () => {
    await renderPage(salesWithManage)

    expect(queryDelete()).toBeInTheDocument()
  })

  it('④ 只持同域但独立的 email:manage / campaign:delete 不构成放行——「删除」仍旧不可见', async () => {
    await renderPage({ ...salesNoPerm, permissions: ['email:manage', 'campaign:delete'] })

    // 反空洞守卫：同一渲染里数据行与「编辑」都在，排除"页面空了"
    expect(screen.getByText('双十一活动落地页')).toBeInTheDocument()
    expect(screen.getByText('pages.landing.btnEdit')).toBeInTheDocument()
    expect(queryDelete()).not.toBeInTheDocument()
  })

  it('⑤ 有码时「删除」确实接到删除端点：确认后以该行 id 调用 deleteLandingPage', async () => {
    const { deleteLandingPage } = await import('../../services/landingPageService')
    vi.mocked(deleteLandingPage).mockResolvedValue(undefined)
    await renderPage(salesWithManage)

    fireEvent.click(queryDelete() as HTMLElement)
    // 确认气泡出现，说明这个链接确实是「删除」动作（而不是只剩个装饰性的红字）
    expect(await screen.findByText('pages.landing.confirmDelete', {}, { timeout: 5000 })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /确\s*定/ }))

    await waitFor(() => expect(deleteLandingPage).toHaveBeenCalledWith(row.id))
  })
})
