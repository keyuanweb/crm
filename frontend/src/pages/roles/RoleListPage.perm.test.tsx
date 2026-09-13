import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import RoleListPage from './RoleListPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/roleService', () => ({
  fetchRoles: vi.fn(),
  createRole: vi.fn(),
  updateRole: vi.fn(),
  deleteRole: vi.fn(),
  fetchRoleOptions: vi.fn(async () => []),
  // 页面挂载时会 fetchMenuTree() / fetchPermissionDefs()（新增/编辑弹窗的勾选字典），
  // 不挡掉就会真发请求。
  fetchMenuTree: vi.fn(async () => []),
  fetchPermissionDefs: vi.fn(async () => []),
}))

/**
 * 087 追补 · `RoleListPage` 的渲染测试（`role:manage`——**一码管两处**）。
 *
 * <p>判据在 `RoleListPage.tsx:58`：`const canManage = hasPerm(PERMS.roleManage, user)`。
 * 这一个码同时管着**两个位置**，二者必须同进同出，所以每条用例都同时断言两处：
 *
 * <ul>
 *   <li>**行内** `:199`：`canManage ? [编辑, (!row.builtIn ? 删除 : 内建占位)] : []` —— 整列渲染成空数组；</li>
 *   <li>**工具栏** `:263`：`toolBarRender={() => canManage ? [新建] : []}` —— 连按钮元素都不产出。</li>
 * </ul>
 *
 * <p><b>锚点防真空</b>：每条用例都同时断言页面标题、两行数据本身在文档里——否则整页渲染失败
 * 会被误判成"按钮正确地不在"。行内控件用 `queryAllByText(...).length` 计数而不是 `getByText`
 * （多行会同时命中，`getByText` 在多元素匹配时**抛错**）。
 *
 * <p><b>内置角色另有一道判据</b>（`!row.builtIn`，与权限判据是 ∧）：造两条可分的数据
 * （一条 `builtIn: false`、一条 `builtIn: true`），于是"有码"时删除链接恰好只该出现 **1** 次——
 * 写成 2 就会把"内置角色被误当成可删"这种缺陷放过去。若把 `!row.builtIn` 改成恒真，
 * 这条计数断言立刻转红（已验证）。
 *
 * <p>负向用例**必须**用非 ADMIN：`hasPerm` 对 `role === 'ADMIN'` 直通返回 true
 * （`hooks/usePermission.ts:10`），拿管理员永远测不出「看不见」。
 */
const salesRoleRow = {
  id: 1,
  code: 'SALES',
  name: '销售角色A',
  description: '跟进客户',
  dataScope: 'SELF',
  enabled: true,
  // 非内置：删除链接应当出现（在持有 role:manage 的前提下）
  builtIn: false,
  menus: [],
  permissions: [],
}
const builtInRoleRow = {
  id: 2,
  code: 'ADMIN',
  name: '系统管理员角色',
  description: '内建角色',
  dataScope: 'ALL',
  enabled: true,
  // 内置：即使持有 role:manage，此处也**不得**出现删除链接，只出现「内建」占位文案
  builtIn: true,
  menus: [],
  permissions: [],
}

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码——"看不见"的对照组必须建立在它之上。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 只多了 `role:manage`，其余与基线逐字相同——把差异收敛到唯一变量。 */
const salesWithPerm: UserInfo = { ...salesNoPerm, permissions: ['role:manage'] }

/** 行内「编辑」——两行都会渲染，故只能计数。 */
const countEdit = () => screen.queryAllByText('pages.roleList.btnEdit').length
/** 行内「删除」——只有非内置角色那一行该有，故恰好为 0 或 1。 */
const countDelete = () => screen.queryAllByText('pages.roleList.btnDelete').length
/** 工具栏「新建」——受同一个码管辖的第二处。 */
const queryCreate = () => screen.queryByText('pages.roleList.btnCreate')
/** 内置角色的占位文案（替代删除链接渲染）。 */
const queryBuiltinLabel = () => screen.queryByText('pages.roleList.builtinLabel')

async function renderPage(user: UserInfo) {
  useAuthStore.setState({ user })
  const { fetchRoles } = await import('../../services/roleService')
  vi.mocked(fetchRoles).mockResolvedValue({
    items: [salesRoleRow, builtInRoleRow],
    total: 2,
    page: 1,
    pageSize: 20,
  } as never)

  renderWithProviders(<RoleListPage />, { route: '/roles' })
  // 反空洞守卫：等到表格真的渲染出这两行，后续的否定断言才有意义 ——
  // 否则"按钮不在"可能只是"页面还没加载出来"，那样的负向断言是假绿。
  await screen.findByText('销售角色A')
  await screen.findByText('系统管理员角色')
}

describe('RoleListPage 的权限接线（087 T017 · role:manage，一码管两处）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('① ADMIN 看得见行内「编辑/删除」与工具栏「新建」', async () => {
    await renderPage(adminUser)

    expect(screen.getByText('pages.roleList.title')).toBeInTheDocument()
    expect(countEdit()).toBe(2)
    expect(countDelete()).toBe(1)
    expect(queryCreate()).toBeInTheDocument()
  })

  it('② 无 role:manage 的 SALES 两处都看不见（负向；页面与两行数据照常渲染）', async () => {
    await renderPage(salesNoPerm)

    // 反空洞守卫：页面标题与两行数据都在——证明渲染已完成，不见的确实是被权限管辖的两处。
    expect(screen.getByText('pages.roleList.title')).toBeInTheDocument()
    expect(screen.getByText('销售角色A')).toBeInTheDocument()
    expect(screen.getByText('系统管理员角色')).toBeInTheDocument()
    // 行内一处：
    expect(countEdit()).toBe(0)
    expect(countDelete()).toBe(0)
    // 工具栏一处（同一个码，必须同进同出）：
    expect(queryCreate()).not.toBeInTheDocument()
  })

  it('③ 持有 role:manage 的 SALES 行内与工具栏**两处都**看得见（正向；锁住码的身份）', async () => {
    await renderPage(salesWithPerm)

    expect(screen.getByText('销售角色A')).toBeInTheDocument()
    expect(countEdit()).toBe(2)
    expect(queryCreate()).toBeInTheDocument()
    // 一码管两处：两处必须同进同出，故在同一用例里一起断言（上面已断工具栏、这里断行内）。
    expect(countDelete()).toBe(1)
  })

  it('④ 持码时内置角色仍不可删（`!row.builtIn` 闸门没被权限吞掉）', async () => {
    await renderPage(salesWithPerm)

    expect(screen.getByText('销售角色A')).toBeInTheDocument()
    expect(screen.getByText('系统管理员角色')).toBeInTheDocument()
    // 两行都有「编辑」⇒ 行内权限判据放行了……
    expect(countEdit()).toBe(2)
    // ……但删除只出现 1 次：内置那一行走的是「内建」占位，不是删除链接。
    expect(countDelete()).toBe(1)
    expect(queryBuiltinLabel()).toBeInTheDocument()
  })
})
