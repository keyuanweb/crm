import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import DepartmentListPage from './DepartmentListPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/departmentService', () => ({
  fetchDepartmentTree: vi.fn(),
  createDepartment: vi.fn(),
  updateDepartment: vi.fn(),
  deleteDepartment: vi.fn(),
}))

/**
 * 086 权限收口 · `DepartmentListPage` 删除按钮的双向渲染测试。
 *
 * <p>一页只有一处收口（行内「删除」，`department:manage`——`DepartmentController.java:71-73`）。
 * 三条断言的形态与 `ContractTemplateListPage.perm.test.tsx` 相同：ADMIN 看得见、无码的非 ADMIN
 * 看不见、**有码的非 ADMIN 看得见**。
 *
 * <p><b>关于"这道判据当下是冗余的"</b>：本页取数的 `GET /departments/tree`（`:48-50`）挂的是**同一个**
 * `department:manage`，所以"能渲染出这棵树"本身就蕴含"持有该码"。这意味着本文件第 3 例
 * （有码 ⇒ 可见）在今天**也是**"能加载出页面 ⇒ 可见"——它并不比第 1 例多证明"隐藏有效"。
 * 它仍然必须存在：它锁住的是**码的身份**。若有人把判据改成别的码（或死码），第 3 例会红。
 * 真正的"隐藏"能力要等后端把读与写拆成两个码之后才出现（页面注释里记了这一点）。
 */
const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码。负向用例必须建立在它之上（ADMIN 在 hasPerm 里直通）。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
const salesWithPerm: UserInfo = { ...salesNoPerm, permissions: ['department:manage'] }

/** 单个无子节点的部门：Tree 的顶层节点无论是否展开都会渲染，故其行内动作一定可见。 */
const deptTree = [{ id: 1, name: '销售部', parentId: null, children: [], childCount: 0, memberCount: 0 }]

const deleteButton = () => screen.queryByRole('button', { name: /pages\.departmentList\.btnDelete/ })

async function renderPage(user: UserInfo) {
  const { fetchDepartmentTree } = await import('../../services/departmentService')
  vi.mocked(fetchDepartmentTree).mockResolvedValue(deptTree as never)
  useAuthStore.setState({ user })

  renderWithProviders(<DepartmentListPage />)

  // 反空洞守卫：用**不受权限影响**的「新建」按钮 + 节点名一起确认页面真的渲染完了。
  // 「新建」按钮按 086 决策 1（新建不收口）始终渲染，所以它是最好的"页面已就绪"锚点。
  await screen.findByText((c) => c.includes('销售部'), {}, { timeout: 5000 })
  expect(screen.getByRole('button', { name: /pages\.departmentList\.btnAdd/ })).toBeInTheDocument()
}

describe('DepartmentListPage 删除收口（086）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('① ADMIN 看得见「删除」（且「编辑」「详情」不受影响）', async () => {
    await renderPage(adminUser)

    expect(deleteButton()).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /pages\.departmentList\.btnEdit/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /pages\.departmentList\.btnDetail/ })).toBeInTheDocument()
  })

  it('② 无 department:manage 的 SALES 看不见「删除」，但仍看得见「编辑」（只有删除被收口）', async () => {
    await renderPage(salesNoPerm)

    expect(deleteButton()).not.toBeInTheDocument()
    // 关键对照：同一行里的「编辑」**没有**被收口（决策 1：编辑保持现状）——
    // 这条断言证明上面那条"看不见"是权限判据的结果，而不是整行动作都没渲染出来。
    expect(screen.getByRole('button', { name: /pages\.departmentList\.btnEdit/ })).toBeInTheDocument()
  })

  it('③ 持有 department:manage 的 SALES 看得见「删除」（锁住码的身份）', async () => {
    await renderPage(salesWithPerm)

    expect(deleteButton()).toBeInTheDocument()
  })
})
