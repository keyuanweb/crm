import { describe, expect, it, vi, beforeEach } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import AnnouncementPage from './AnnouncementPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'
import type { Announcement } from '../../types/announcement'

vi.mock('../../services/announcementService', () => ({
  createAnnouncement: vi.fn(),
  deleteAnnouncement: vi.fn(),
  fetchAnnouncements: vi.fn(),
  markAnnouncementRead: vi.fn(),
  updateAnnouncement: vi.fn(),
}))

/**
 * 086 权限收口 · `AnnouncementPage`「删除公告」的双向渲染测试。
 *
 * <p><b>控件 → 判据 → 码</b>：行内动作列的第三格（`Popconfirm` 包着的红色「删除」链接）由
 * `can[PERMS.announcementManage]` 决定是否渲染，`PERMS.announcementManage === 'announcement:manage'`
 * （`constants/permissions.ts:105`）。后端对得上：`AnnouncementController.java:56-57` 的
 * `@DeleteMapping("/{id}")` 挂的正是 `announcement:manage`；
 * service 侧 `deleteAnnouncement` → `DELETE /announcements/{id}`（`services/announcementService.ts:25-27`）。
 *
 * <p><b>本页是「真正收窄」的一页</b>（与 `OpenPlatformPage` / `ApprovalFlowPage` 不同）：同控制器的
 * 列表读端点 `GET /announcements`（`AnnouncementController.java:33`）**不挂任何码**，所以非持有者
 * 照样能打开这张表、看见公告内容——「删除不见」是纯粹的权限效果，不是"页面根本没数据"。
 * 负向用例因此必须把「真实内容已渲染」作为前置断言（第 2 例）。
 *
 * <p><b>刻意不收口的两处</b>（不是遗漏，是 086 的范围界定）：工具栏「发布公告」与行内「编辑」。
 * 本文件把它们当作**反空洞守卫**用：无码用户必须仍然看得见这两个入口，才证明页面本身渲染正常。
 *
 * <p>负向用例**必须**用非 ADMIN：`hasPerm` 对 `role === 'ADMIN'` 直通返回 true
 * （`hooks/usePermission.ts:10`），管理员永远测不出「看不见」。
 */
const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码——"看不见"的对照组必须建立在它之上。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 只多了 `announcement:manage`，其余与基线逐字相同——把差异收敛到唯一变量。 */
const salesWithManage: UserInfo = { ...salesNoPerm, permissions: ['announcement:manage'] }

/** 未读 + 置顶的公告：标题、置顶 Tag、未读 Tag、「标记已读」「编辑」三个不受权限影响的入口全都在这一行上。 */
const row: Announcement = {
  id: 1,
  title: '季度目标发布',
  content: '<p>本季度目标</p>',
  pinned: true,
  read: false,
  createdAt: '2026-09-01T10:20:30',
}

const queryDelete = () => screen.queryByText('pages.announcement.action.delete')
const queryEdit = () => screen.queryByText('pages.announcement.action.edit')

async function renderPage(user: UserInfo) {
  useAuthStore.setState({ user })
  const { fetchAnnouncements } = await import('../../services/announcementService')
  vi.mocked(fetchAnnouncements).mockResolvedValue({ items: [row], total: 1, page: 1, pageSize: 10 } as never)
  renderWithProviders(<AnnouncementPage />)
  // 反空洞守卫：标题来自被 mock 的接口数据，它出现即证明表格 request 已跑通、行已渲染。
  await screen.findByText('季度目标发布', {}, { timeout: 5000 })
}

describe('AnnouncementPage「删除公告」的权限收口（086）', () => {
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

    // 反空洞守卫：先把「与权限无关的真实内容」逐条钉住，否则下面的"删除不在"可能只是整页没渲染
    expect(screen.getByText('季度目标发布')).toBeInTheDocument()
    expect(screen.getByText('pages.announcement.tags.pinned')).toBeInTheDocument()
    expect(screen.getByText('pages.announcement.tags.unread')).toBeInTheDocument()
    // 工具栏「发布公告」与行内「编辑」刻意未收口（086 只收破坏性动作，新建/编辑不在范围内）
    expect(screen.getByRole('button', { name: /pages\.announcement\.toolbar\.publish/ })).toBeInTheDocument()
    expect(queryEdit()).toBeInTheDocument()
    expect(screen.getByText('pages.announcement.action.markRead')).toBeInTheDocument()

    expect(queryDelete()).not.toBeInTheDocument()
  })

  it('③ 持 announcement:manage 的 SALES：「删除」可见（锁住码的身份，非 ADMIN 亦可）', async () => {
    await renderPage(salesWithManage)

    expect(queryDelete()).toBeInTheDocument()
  })

  it('④ 只持别的码（workflow:manage / marketing:manage）不构成放行——「删除」仍旧不可见', async () => {
    await renderPage({ ...salesNoPerm, permissions: ['workflow:manage', 'marketing:manage'] })

    // 反空洞守卫：同一渲染里「编辑」在、数据行在，排除"页面空了"
    expect(screen.getByText('季度目标发布')).toBeInTheDocument()
    expect(queryEdit()).toBeInTheDocument()
    expect(queryDelete()).not.toBeInTheDocument()
  })

  it('⑤ 有码时「删除」确实接到删除端点：确认后以该行 id 调用 deleteAnnouncement', async () => {
    const { deleteAnnouncement } = await import('../../services/announcementService')
    vi.mocked(deleteAnnouncement).mockResolvedValue(undefined)
    await renderPage(salesWithManage)

    fireEvent.click(queryDelete() as HTMLElement)
    // 确认气泡出现，说明这个链接确实是「删除」动作（而不是只剩个装饰性的红字）
    await screen.findByText('pages.announcement.confirmDelete', {}, { timeout: 5000 })
    fireEvent.click(screen.getByRole('button', { name: /确\s*定/ }))

    await waitFor(() => expect(deleteAnnouncement).toHaveBeenCalledWith(1))
  })
})
