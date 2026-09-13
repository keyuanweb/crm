import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import CustomObjectListPage from './CustomObjectListPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/customObjectService', () => ({
  fetchCustomObjects: vi.fn(),
  createCustomObject: vi.fn(),
  updateCustomObject: vi.fn(),
  toggleCustomObject: vi.fn(),
  deleteCustomObject: vi.fn(),
}))

/**
 * 086 权限收口 · `CustomObjectListPage` 的**两个码**渲染测试。
 *
 * <p>启停走 `POST /custom-objects/{id}/toggle`（`custom_object:update`）、删除走
 * `DELETE /custom-objects/{id}`（`custom_object:delete`）——**两个端点挂的是不同的码**，
 * 页面也刻意申请了两个（`CustomObjectListPage.tsx:36`），所以不能一刀切测试。本文件用
 * **交叉断言**把它钉死：只持 update 的人看不见删除、只持 delete 的人看不见启停。
 * 哪天有人图省事把两个判据合并成一个码，第 3、4 例会立刻红——而删除是不可逆操作，
 * 判据合并正是本页最该防的退化。
 *
 * <p>负向用例**必须**用非 ADMIN：`hasPerm` 对 `role === 'ADMIN'` 直通返回 true
 * （`hooks/usePermission.ts:10`），拿管理员永远测不出「看不见」。
 */
const customObjectRow = {
  id: 1,
  name: '设备档案',
  code: 'EQUIPMENT',
  fields: [{ field: 'name', label: '名称', type: 'TEXT' as const, required: true }],
  enabled: true,
  version: 0,
}

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码——"看不见"的对照组必须建立在它之上。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 只多了启停码。 */
const salesToggleOnly: UserInfo = { ...salesNoPerm, permissions: ['custom_object:update'] }
/** 只多了删除码。 */
const salesDeleteOnly: UserInfo = { ...salesNoPerm, permissions: ['custom_object:delete'] }

/** 启停链接（文案随行的 enabled 变，本行是启用中，故为「停用」）。 */
const queryToggle = () => screen.queryByText('pages.customObject.disable')
/** 删除链接。 */
const queryDelete = () => screen.queryByText('pages.customObject.delete')
/** 同一 action 列里**不受权限管辖**的兄弟控件，用来证明该列本身渲染出来了。 */
const queryEdit = () => screen.queryByText('pages.customObject.edit')

async function renderPage(user: UserInfo) {
  useAuthStore.setState({ user })
  const { fetchCustomObjects } = await import('../../services/customObjectService')
  vi.mocked(fetchCustomObjects).mockResolvedValue({
    items: [customObjectRow],
    total: 1,
    page: 1,
    pageSize: 20,
  } as never)

  renderWithProviders(<CustomObjectListPage />)
  // 反空洞守卫：等到表格真的渲染出这一行，否定断言才有意义。
  await screen.findByText('设备档案')
}

describe('CustomObjectListPage 启停与删除的两个码（086）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('ADMIN：启停与删除都可见', async () => {
    await renderPage(adminUser)

    expect(queryToggle()).toBeInTheDocument()
    expect(queryDelete()).toBeInTheDocument()
  })

  it('零权限码的 SALES：两者都不可见（页面与 action 列本身照常渲染）', async () => {
    await renderPage(salesNoPerm)

    // 反空洞守卫：数据行在，同一 action 列的「编辑」链接也在——证明列渲染过了，
    // 不见的确实只是那两个受权限管辖的控件。
    expect(screen.getByText('设备档案')).toBeInTheDocument()
    expect(queryEdit()).toBeInTheDocument()
    expect(queryToggle()).not.toBeInTheDocument()
    expect(queryDelete()).not.toBeInTheDocument()
  })

  it('只持 custom_object:update 的 SALES：看得见启停，看不见删除', async () => {
    await renderPage(salesToggleOnly)

    // ③ 这一条证明该码真的能授予（不是"因别的什么原因没渲染"）
    expect(queryToggle()).toBeInTheDocument()
    // 交叉断言：两个码不得互相放行
    expect(queryDelete()).not.toBeInTheDocument()
  })

  it('只持 custom_object:delete 的 SALES：看得见删除，看不见启停', async () => {
    await renderPage(salesDeleteOnly)

    expect(queryDelete()).toBeInTheDocument()
    expect(queryToggle()).not.toBeInTheDocument()
  })
})
