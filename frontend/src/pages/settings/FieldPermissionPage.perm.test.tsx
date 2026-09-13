import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen, within } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import FieldPermissionPage from './FieldPermissionPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/fieldPermissionService', () => ({
  fetchFieldPermissions: vi.fn(),
  upsertFieldPermission: vi.fn(),
  deleteFieldPermission: vi.fn(),
}))
// 页面另外间接依赖这两个服务：`fetchRoleOptions` 在挂载时的 useEffect 里发一次（`:50-59`），
// `fetchCustomFields` 在表单里选实体类型时发（`:67-74`）。不挡掉就是真请求（XHR 已被 setup 静默成
// 永不返回）。本文件只测渲染层，两者都不会被断言。
vi.mock('../../services/roleService', () => ({
  fetchRoleOptions: vi.fn(),
}))
vi.mock('../../services/customFieldService', () => ({
  fetchCustomFields: vi.fn(),
}))

/**
 * 086 权限收口 · `FieldPermissionPage` 行内「删除」的渲染测试。
 *
 * <p>被测控件：action 列里包在 `Popconfirm` 内的行内「删除」链接
 * （`FieldPermissionPage.tsx:99-105`——该列 `render` 返回的是**数组**，无码时唯一的那个位置是
 * `false`，React 直接跳过，整列渲染为空）。判据是 `can[PERMS.fieldPermissionManage]`，
 * 码为 `field_permission:manage`（`constants/permissions.ts:252`）——与页面注释（`:46-47`）一致。
 *
 * <p>该码经后端核对无误：删除配置打 `DELETE /api/v1/field-permissions/{id}`，
 * `FieldPermissionController.java:61-62` 上挂的正是 `@RequirePermission("field_permission:manage")`
 * （与 upsert 同一个码，页面注释也这么写的）。
 *
 * <p><b>本页没有"可用来当守卫的兄弟控件"</b>：action 列里只有这一个受管辖的删除，
 * 没有第二个不受权限管辖的链接（对比 `SlaPolicyListPage` 的「编辑」）。因此反空洞守卫改由
 * **真实数据行**承担——用行里那个唯一的 `fieldId` 数值（`:86`，`dataIndex` 直出、无 render、
 * 与 i18n / enumLabels 都无关）作为"该行确实渲染过"的证据。这样"删除不在"才不会被
 * "整张表压根没渲染出来"冒充。
 *
 * <p>负向用例**必须**用非 ADMIN：`hasPerm` 对 `role === 'ADMIN'` 直通返回 true
 * （`hooks/usePermission.ts:10`），拿管理员永远测不出「看不见」。
 */
const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码——"看不见"的对照组必须建立在它之上。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 只多了 `field_permission:manage`，其余与基线逐字相同——把差异收敛到唯一变量。 */
const salesWithPerm: UserInfo = { ...salesNoPerm, permissions: ['field_permission:manage'] }

/** 一条普通配置行。`fieldId` 取一个不会与任何标题/枚举文案撞车的数值，专供行定位用。 */
const permRow = {
  id: 5,
  roleCode: 'SALES',
  entityType: 'LEAD',
  fieldId: 8842,
  permission: 'READ_ONLY' as const,
}

const LBL_DELETE = 'pages.fieldPermission.btnDelete'
/** 反空洞守卫锚点：该行 `fieldId` 单元格的**字面渲染结果**。 */
const ROW_ANCHOR = '8842'

async function renderPage(user: UserInfo) {
  useAuthStore.setState({ user })
  const { fetchFieldPermissions } = await import('../../services/fieldPermissionService')
  const { fetchRoleOptions } = await import('../../services/roleService')
  vi.mocked(fetchFieldPermissions).mockResolvedValue({ items: [permRow], total: 1, page: 1, pageSize: 20 } as never)
  vi.mocked(fetchRoleOptions).mockResolvedValue([{ code: 'SALES', name: '销售' }] as never)

  renderWithProviders(<FieldPermissionPage />)
  // 反空洞守卫：等到表格真的渲染出这一行（`fieldId` 由行数据直出），否定断言才有意义 ——
  // 否则"按钮不在"可能只是"整张表还没加载出来"，那样的负向断言是假绿。
  await screen.findByText(ROW_ANCHOR)
}

/** 按 `fieldId` 单元格定位到 `<tr>`。 */
function row(): HTMLElement {
  const tr = screen.getByText(ROW_ANCHOR).closest('tr')
  if (!tr) throw new Error(`未找到 fieldId=${ROW_ANCHOR} 所在的行`)
  return tr as HTMLElement
}

describe('FieldPermissionPage 行内删除的权限收口（086）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('ADMIN 看得见行内「删除」；行内其余单元格照常渲染', async () => {
    await renderPage(adminUser)

    // 数据行确实渲染了：角色 / 实体 / 权限三格都由行数据映射而来
    expect(within(row()).queryByText('enums.userRole.sales')).toBeInTheDocument()
    expect(within(row()).queryByText('enums.entity.lead')).toBeInTheDocument()
    expect(within(row()).queryByText('enums.fieldPermission.readOnly')).toBeInTheDocument()
    expect(within(row()).queryByText(LBL_DELETE)).toBeInTheDocument()
  })

  it('无 field_permission:manage 的 SALES 看不见「删除」（数据行与工具栏按钮照常渲染）', async () => {
    await renderPage(salesNoPerm)

    // 反空洞守卫（其一）：真实数据行在，且行内三个由数据映射出的单元格都在——
    // 证明表格渲染过了，不见的确实只是那个受权限管辖的控件。
    expect(screen.getByText(ROW_ANCHOR)).toBeInTheDocument()
    expect(within(row()).queryByText('enums.userRole.sales')).toBeInTheDocument()
    expect(within(row()).queryByText('enums.fieldPermission.readOnly')).toBeInTheDocument()
    // 反空洞守卫（其二）：「新建」按钮不受本判据管辖（`:144-156`），它是页面外壳活着的旁证。
    expect(screen.getByRole('button', { name: /pages\.fieldPermission\.btnAdd/ })).toBeInTheDocument()

    expect(within(row()).queryByText(LBL_DELETE)).not.toBeInTheDocument()
  })

  it('持有 field_permission:manage 的 SALES 看得见「删除」（证明该码可授予，而非 ADMIN 特权）', async () => {
    await renderPage(salesWithPerm)

    expect(within(row()).queryByText(LBL_DELETE)).toBeInTheDocument()
  })
})
