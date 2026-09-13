import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen, within } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import CustomFieldListPage from './CustomFieldListPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/customFieldService', () => ({
  fetchCustomFields: vi.fn(),
  createCustomField: vi.fn(),
  updateCustomField: vi.fn(),
  deleteCustomField: vi.fn(),
}))

/**
 * 086 权限收口 · `CustomFieldListPage` 行内「删除」的渲染测试。
 *
 * <p>被测控件：action 列里包在 `Popconfirm` 内的行内「删除」链接
 * （`CustomFieldListPage.tsx:154-162`——注意该列 `render` 返回的是**数组**，无码时那个位置是
 * `false`，React 直接跳过）。判据是 `can[PERMS.customFieldDelete]`，码为 `custom_field:delete`
 * （`constants/permissions.ts:248`）——与页面注释（`:36`）一致。
 *
 * <p>该码经后端核对无误：删除字段打 `DELETE /api/v1/custom-fields/{id}`，
 * `CustomFieldController.java:86-87` 上挂的正是 `@RequirePermission("custom_field:delete")`。
 * （同文件的 update 是另一个码，本页的「编辑」因此不受本判据管辖。）
 *
 * <p>同一 action 列里**不受权限管辖**的兄弟控件是「编辑」（`:151-153`，无判据包裹）——
 * 它是"该列确实渲染过"的证据，缺了它，"删除不在"就可能是整张表没渲染出来的假绿。
 *
 * <p>负向用例**必须**用非 ADMIN：`hasPerm` 对 `role === 'ADMIN'` 直通返回 true
 * （`hooks/usePermission.ts:10`），拿管理员永远测不出「看不见」。
 */
const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码——"看不见"的对照组必须建立在它之上。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 只多了 `custom_field:delete`，其余与基线逐字相同——把差异收敛到唯一变量。 */
const salesWithPerm: UserInfo = { ...salesNoPerm, permissions: ['custom_field:delete'] }

/** 一条普通自定义字段行；名称在列表里由行数据直接渲染，用作定位锚点。 */
const fieldRow = {
  id: 11,
  entityType: 'LEAD' as const,
  name: '客户行业',
  fieldType: 'TEXT' as const,
  required: false,
  enabled: true,
  sortOrder: 3,
  version: 0,
}

const LBL_DELETE = 'pages.customField.delete'
const LBL_EDIT = 'pages.customField.edit'

async function renderPage(user: UserInfo) {
  useAuthStore.setState({ user })
  const { fetchCustomFields } = await import('../../services/customFieldService')
  vi.mocked(fetchCustomFields).mockResolvedValue({ items: [fieldRow], total: 1, page: 1, pageSize: 20 } as never)

  renderWithProviders(<CustomFieldListPage />)
  // 反空洞守卫：等到表格真的渲染出这一行（字段名由行数据渲染而来），否定断言才有意义 ——
  // 否则"按钮不在"可能只是"整张表还没加载出来"，那样的负向断言是假绿。
  await screen.findByText('客户行业')
}

/** 按字段名单元格定位到 `<tr>`，行内查询不受同页其他文案干扰。 */
function row(): HTMLElement {
  const tr = screen.getByText('客户行业').closest('tr')
  if (!tr) throw new Error('未找到「客户行业」所在的行')
  return tr as HTMLElement
}

describe('CustomFieldListPage 行内删除的权限收口（086）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('ADMIN 看得见行内「删除」与「编辑」', async () => {
    await renderPage(adminUser)

    expect(within(row()).queryByText(LBL_EDIT)).toBeInTheDocument()
    expect(within(row()).queryByText(LBL_DELETE)).toBeInTheDocument()
  })

  it('无 custom_field:delete 的 SALES 看不见「删除」（数据行与同列「编辑」照常渲染）', async () => {
    await renderPage(salesNoPerm)

    // 反空洞守卫：数据行在，同一 action 列里不受权限管辖的「编辑」也在——
    // 证明列渲染过了，不见的确实只是那个受权限管辖的控件。
    expect(screen.getByText('客户行业')).toBeInTheDocument()
    expect(within(row()).queryByText(LBL_EDIT)).toBeInTheDocument()
    expect(within(row()).queryByText(LBL_DELETE)).not.toBeInTheDocument()
  })

  it('持有 custom_field:delete 的 SALES 看得见「删除」（证明该码可授予，而非 ADMIN 特权）', async () => {
    await renderPage(salesWithPerm)

    expect(within(row()).queryByText(LBL_DELETE)).toBeInTheDocument()
  })
})
