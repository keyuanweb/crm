import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import UserManagementPage from './UserManagementPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/userService', () => ({
  fetchUsers: vi.fn(),
  createUser: vi.fn(),
  updateUser: vi.fn(),
  resetPassword: vi.fn(),
  resetUserMfa: vi.fn(),
}))
vi.mock('../../services/departmentService', () => ({
  fetchDepartmentTree: vi.fn(async () => []),
  setUserDataPermission: vi.fn(),
  createDepartment: vi.fn(),
  updateDepartment: vi.fn(),
  deleteDepartment: vi.fn(),
}))

/**
 * 086 权限收口 · `UserManagementPage` 的**一码四处**渲染测试（082 由三处增至四处）。
 *
 * <p>行内四个动作打四个端点，但挂的是**同一个码**：
 *
 * <pre>
 *   数据权限    PUT  /users/{id}/data-permission  ┐
 *   重置密码    PUT  /users/{id}/password         │
 *   重置 2FA    POST /users/{id}/2fa/reset        ├─→ user:manage
 *   启用/停用   PUT  /users/{id}                  ┘
 * </pre>
 *
 * <p>「一页多个控件、同一个码」与「一页多个控件、多个码」的断言结构不同：这里四个控件必须
 * **同生同灭**，所以每条用例四个断言一起给（给一个漏一个就等于没测）。两个方向都要有：
 * 无码时四个都不在、有码时四个都在——只有后者能证明这个码真的被授得出去。
 *
 * <p>「编辑」{@code (:237-239)} 刻意不在收口范围内（086 只收删除类与高权动作），每条用例里
 * 它都必须照旧可见——它同时充当反空洞守卫之外的第二个「整页确实渲染了」的证据。
 *
 * <p>负向用例**必须**用非 ADMIN 用户：`hasPerm` 对 `role === 'ADMIN'` 直接短路返回 true
 * （`hooks/usePermission.ts:10`），用管理员永远拿不到「看不见」的结论。
 */

const ROW_USERNAME = 'sales01'

const userRow = {
  id: 2,
  username: ROW_USERNAME,
  displayName: '销售一',
  role: 'SALES',
  enabled: true,
  version: 0,
}

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }

/** 非 ADMIN 基线：零权限码。负向用例必须建立在它之上（ADMIN 在 hasPerm 里直通）。 */
const supportNoPerm: UserInfo = { id: 3, username: 'support01', displayName: '客服一', role: 'SUPPORT', permissions: [] }
/** 只多这一个码，其余与基线逐字相同——把差异收敛到唯一变量。 */
const supportWithPerm: UserInfo = { ...supportNoPerm, permissions: ['user:manage'] }

const linkDataPermission = () => screen.queryByText('pages.userManagement.dataPermission')
const linkResetPassword = () => screen.queryByText('pages.userManagement.resetPassword')
/** 重置 2FA（082 新增，第四处）。它只在 Popconfirm 的触发链接上——确认框里的文案是另一个键。 */
const linkReset2fa = () => screen.queryByText('pages.userManagement.reset2fa')
/** 启停链接：行 enabled=true 时文案是「停用」（状态列的 Tag 此刻显示的是 `enable`，键不同，不会混淆）。 */
const linkToggle = () => screen.queryByText('pages.userManagement.disable')
/** 「编辑」不在收口范围内：每条用例里都必须可见。 */
const linkEdit = () => screen.queryByText('pages.userManagement.edit')

async function renderPage(user: UserInfo) {
  useAuthStore.setState({ user })
  const { fetchUsers } = await import('../../services/userService')
  vi.mocked(fetchUsers).mockResolvedValue({ items: [userRow], total: 1, page: 1, pageSize: 20 } as never)

  renderWithProviders(<UserManagementPage />)
  // 等到表格真的渲染出这一行，后续的否定断言才有意义 ——
  // 否则「按钮不在」可能只是「页面还没加载出来」，那样的负向断言是假绿。
  await screen.findByText(ROW_USERNAME, {}, { timeout: 5000 })
}

describe('UserManagementPage 一码四处收口（086 / 082）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('① ADMIN：数据权限 / 重置密码 / 重置 2FA / 停用 都看得见（收口未误伤管理员）', async () => {
    await renderPage(adminUser)

    expect(linkDataPermission()).toBeInTheDocument()
    expect(linkResetPassword()).toBeInTheDocument()
    expect(linkReset2fa()).toBeInTheDocument()
    expect(linkToggle()).toBeInTheDocument()
    expect(linkEdit()).toBeInTheDocument()
  })

  it('② 无 user:manage 的 SUPPORT：四处全不可见（先确认页面已渲染）', async () => {
    await renderPage(supportNoPerm)

    // 反空洞守卫：用户行确实渲染出来了，否定断言才不是假绿
    expect(screen.getByText(ROW_USERNAME)).toBeInTheDocument()
    expect(screen.getByText('销售一')).toBeInTheDocument()

    expect(linkDataPermission()).not.toBeInTheDocument()
    expect(linkResetPassword()).not.toBeInTheDocument()
    expect(linkReset2fa()).not.toBeInTheDocument()
    expect(linkToggle()).not.toBeInTheDocument()
    // 「编辑」不在收口范围内，必须**照旧可见**——这条同时证明第 ② 例不是「整页没渲染」
    expect(linkEdit()).toBeInTheDocument()
  })

  it('③ 持 user:manage 的 SUPPORT：四处全可见（该权限从此可授予）', async () => {
    await renderPage(supportWithPerm)

    expect(linkDataPermission()).toBeInTheDocument()
    expect(linkResetPassword()).toBeInTheDocument()
    expect(linkReset2fa()).toBeInTheDocument()
    expect(linkToggle()).toBeInTheDocument()
    expect(linkEdit()).toBeInTheDocument()
  })
})
