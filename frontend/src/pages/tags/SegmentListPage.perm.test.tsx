import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import SegmentListPage from './SegmentListPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/segmentService', () => ({
  fetchSegments: vi.fn(),
  createSegment: vi.fn(),
  updateSegment: vi.fn(),
  deleteSegment: vi.fn(),
  fetchSegmentMembers: vi.fn(),
  serializeCondition: (cond: unknown) => JSON.stringify(cond),
}))
// 页面在 useEffect 里 `import('../../services/tagService')`（条件字段的标签下拉），
// 动态 import 同样会被 vi.mock 拦下；不挡掉就会真发请求。
vi.mock('../../services/tagService', () => ({
  fetchTags: vi.fn(),
}))

/**
 * 086 权限收口 · `SegmentListPage` 行内「删除」的渲染测试。
 *
 * <p><b>本文件存在的唯一理由：钉死删除端点挂的是 `tag:manage`，不是 `segment:manage`。</b>
 * 细分的删除打的是 `DELETE /api/v1/segments/{id}`，而 `SegmentController` 上标的是 `tag:manage`
 * （`SegmentController.java:56-57`）。`segment:manage` 在 `RoleConstants.PERMISSION_DEFS` 里**存在**、
 * 却**没有任何端点校验**——它是死码。字典里存在的码意味着角色页能把它勾出来，所以「误挂死码」不会
 * 报错、不会 403：它对除 ADMIN 以外的**所有人**恒为 false，删除按钮会无声无息地消失。
 *
 * <p>所以第 3 例（`permissions: ['tag:manage']` ⇒ 可见）**必须**用 `PERMS.tagManage` 对应的字面量
 * `'tag:manage'`。若哪天有人"顺手修正"成 `'segment:manage'`，这条断言会立刻红——它就是那把锁。
 *
 * <p>负向用例**必须**用非 ADMIN：`hasPerm` 对 `role === 'ADMIN'` 直通返回 true
 * （`hooks/usePermission.ts:10`），拿管理员永远测不出「看不见」。
 */
const segmentRow = {
  id: 1,
  name: '高价值客户',
  description: '年度合同额 > 100 万',
  conditions: '{"logic":"AND","filters":[]}',
  memberCount: 12,
}

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码——"看不见"的对照组必须建立在它之上。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 只多了 `tag:manage`（**不是** `segment:manage`，后者是死码）。 */
const salesWithPerm: UserInfo = { ...salesNoPerm, permissions: ['tag:manage'] }

/** 行内「删除」链接。 */
const queryDelete = () => screen.queryByText('common.button.delete')
/** 同一 action 列里**不受权限管辖**的兄弟控件，用来证明该列本身渲染出来了。 */
const queryMembers = () => screen.queryByText('pages.segmentList.members')

async function renderPage(user: UserInfo) {
  useAuthStore.setState({ user })
  const { fetchSegments } = await import('../../services/segmentService')
  const { fetchTags } = await import('../../services/tagService')
  vi.mocked(fetchSegments).mockResolvedValue([segmentRow] as never)
  vi.mocked(fetchTags).mockResolvedValue([] as never)

  renderWithProviders(<SegmentListPage />)
  // 反空洞守卫：等到表格真的渲染出这一行，后续的否定断言才有意义 ——
  // 否则"按钮不在"可能只是"页面还没加载出来"，那样的负向断言是假绿。
  await screen.findByText('高价值客户')
}

describe('SegmentListPage 行内删除的权限收口（086）', () => {
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

    // 反空洞守卫：数据行在，且同一 action 列的「成员」链接在——证明列渲染过了，
    // 不见的确实只是那个受权限管辖的控件。
    expect(screen.getByText('高价值客户')).toBeInTheDocument()
    expect(queryMembers()).toBeInTheDocument()
    expect(queryDelete()).not.toBeInTheDocument()
  })

  it('持有 tag:manage 的 SALES 看得见「删除」（若被误改成 segment:manage，本条即红）', async () => {
    await renderPage(salesWithPerm)

    expect(queryDelete()).toBeInTheDocument()
  })
})
