import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import TagListPage from './TagListPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/tagService', () => ({
  fetchTags: vi.fn(),
  createTag: vi.fn(),
  updateTag: vi.fn(),
  deleteTag: vi.fn(),
}))

/**
 * 086 权限收口 · `TagListPage` 行内「删除」的渲染测试。
 *
 * <p>删除标签打的是 `DELETE /api/v1/tags/{id}`，`TagController` 上标的是 `tag:manage`
 * （新建/编辑是同一个码，但本期只收口删除）。三条断言缺一不可：
 * ADMIN 可见 / 无码的非 ADMIN 不可见 / 只有该码的非 ADMIN 可见——第三条才证明
 * 「码本身起了作用」，否则「按钮不在」可能只是页面没渲染出来。
 *
 * <p>负向用例**必须**用非 ADMIN：`hasPerm` 对 `role === 'ADMIN'` 直通返回 true
 * （`hooks/usePermission.ts:10`）。
 */
const tagRow = { id: 1, name: '重点客户', color: 'red', entityType: 'CUSTOMER' }

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 只多了 `tag:manage`，其余逐字相同——把差异收敛到唯一变量。 */
const salesWithPerm: UserInfo = { ...salesNoPerm, permissions: ['tag:manage'] }

/** 行内「删除」链接。 */
const queryDelete = () => screen.queryByText('pages.tagList.delete')
/** 同一 action 列里**不受权限管辖**的兄弟控件，用来证明该列本身渲染出来了。 */
const queryEdit = () => screen.queryByText('pages.tagList.edit')

async function renderPage(user: UserInfo) {
  useAuthStore.setState({ user })
  const { fetchTags } = await import('../../services/tagService')
  vi.mocked(fetchTags).mockResolvedValue([tagRow] as never)

  renderWithProviders(<TagListPage />)
  // 反空洞守卫：等到表格真的渲染出这一行，否定断言才有意义。
  await screen.findByText('重点客户')
}

describe('TagListPage 行内删除的权限收口（086）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('ADMIN 看得见行内「删除」', async () => {
    await renderPage(adminUser)

    expect(queryDelete()).toBeInTheDocument()
  })

  it('无 tag:manage 的 SALES 看不见「删除」（页面与 action 列本身照常渲染）', async () => {
    await renderPage(salesNoPerm)

    // 反空洞守卫：数据行在，同一 action 列的「编辑」链接也在——证明列渲染过了。
    expect(screen.getByText('重点客户')).toBeInTheDocument()
    expect(queryEdit()).toBeInTheDocument()
    expect(queryDelete()).not.toBeInTheDocument()
  })

  it('持有 tag:manage 的 SALES 看得见「删除」（证明该码可授予）', async () => {
    await renderPage(salesWithPerm)

    expect(queryDelete()).toBeInTheDocument()
  })
})
