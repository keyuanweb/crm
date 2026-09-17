import { beforeEach, describe, expect, it, vi } from 'vitest'
import { configure, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import { HEAVY_RENDER_ASYNC_TIMEOUT, HEAVY_RENDER_TEST_TIMEOUT } from '../../test/timeouts'
import FieldPermissionPage from './FieldPermissionPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

// 整页渲染型用例（ProTable + Modal + Form）的两个上限，取值依据见 `src/test/timeouts.ts`。
configure({ asyncUtilTimeout: HEAVY_RENDER_ASYNC_TIMEOUT })
vi.setConfig({ testTimeout: HEAVY_RENDER_TEST_TIMEOUT })

/*
  102 的实测口径：本页的字段下拉改调 `available-fields`（内置 + 自定义同源）。
  模块工厂用 `importOriginal` 保留**真实**的 `fieldOptionValue` / `toUpsertField`——
  本文件的判据之一正是它们的编码结果，若在这里重写一遍，测的就是替身而不是产品代码。
*/
vi.mock('../../services/fieldPermissionService', async (importOriginal) => {
  const actual =
    await importOriginal<typeof import('../../services/fieldPermissionService')>()
  return {
    ...actual,
    fetchFieldPermissions: vi.fn(),
    fetchAvailableFields: vi.fn(),
    upsertFieldPermission: vi.fn(),
    deleteFieldPermission: vi.fn(),
  }
})
// 挂载时的 useEffect 会拉一次角色选项；不挡掉就是真请求（XHR 已被 setup 静默成永不返回）。
vi.mock('../../services/roleService', () => ({
  fetchRoleOptions: vi.fn(),
}))

/**
 * 102 · `FieldPermissionPage` 的内置字段配置面（T17）。
 *
 * <p><b>为什么必须有这个文件</b>：本批后端那条掩码链做得再对，只要管理员在下拉里选不到内置字段，
 * 整套权限就等于没有入口。而这一面**没有任何后端用例看着**——它全在浏览器里。
 *
 * <p>三条断言各对应一处真实改动：
 *
 * <ol>
 *   <li>列表的「字段」列改渲染 `fieldName`（此前直出数字 `fieldId`，一排「8842」既看不出是哪个字段，
 *       也分不出内置/自定义）；
 *   <li>字段下拉里**同时**有内置与自定义两组，内置项的标签来自后端（注册表的中文名）；
 *   <li>提交时把选项值拆回请求体：内置字段发 **`fieldKey`**（`fieldId` 为 null）——这是本批给内置字段
 *       "配得出来"的最后一环。
 * </ol>
 *
 * <p>用 ADMIN：本页本就只对 `field_permission:manage` 开放，而 `hasPerm` 对 ADMIN 直通，
 * 于是"按钮在不在"不会变成干扰变量（权限语义由同目录的 `.perm.test.tsx` 管）。
 *
 * <p>⚠️ 与 `.perm.test.tsx` 的分工：那个文件钉的是行内「删除」的可见性，其锚点行**既无
 * `fieldName` 也无 `fieldKey`**，因此仍渲染出数字 `8842`；本文件的数字 id 断言是它的反面
 * （有 `fieldName` 时数字**不得**出现）。两个文件因此互不冲突。
 */
const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }

/** 一条内置字段配置：`fieldId` 为空、靠 `fieldKey` 标识（102 起库里那一列是 NULL）。 */
const builtinRow = {
  id: 7,
  roleCode: 'SALES',
  entityType: 'CUSTOMER',
  fieldId: null,
  fieldKey: 'phone',
  fieldName: '电话',
  permission: 'HIDDEN',
}
/** 一条自定义字段配置：有数字 `fieldId`、没有 `fieldKey`。 */
const customRow = {
  id: 8,
  roleCode: 'SALES',
  entityType: 'CUSTOMER',
  fieldId: 8842,
  fieldName: '客户等级',
  permission: 'READ_ONLY',
}

/** 可配字段：一条内置（phone）+ 一条自定义（8842）。 */
const availableFields = [
  { fieldId: null, fieldKey: 'phone', fieldName: '电话', builtin: true },
  { fieldId: 8842, fieldKey: null, fieldName: '客户等级', builtin: false },
]

async function renderPage() {
  useAuthStore.setState({ user: adminUser })
  const svc = await import('../../services/fieldPermissionService')
  const roleSvc = await import('../../services/roleService')
  vi.mocked(svc.fetchFieldPermissions).mockResolvedValue({
    items: [builtinRow, customRow],
    total: 2,
    page: 1,
    pageSize: 20,
  } as never)
  vi.mocked(svc.fetchAvailableFields).mockResolvedValue({
    items: availableFields,
    total: 2,
    page: 1,
    pageSize: 200,
  } as never)
  vi.mocked(svc.upsertFieldPermission).mockResolvedValue({ ...builtinRow } as never)
  vi.mocked(roleSvc.fetchRoleOptions).mockResolvedValue([
    { code: 'SALES', name: '销售代表' },
  ] as never)

  renderWithProviders(<FieldPermissionPage />)
  // 反空洞守卫：等表格真的渲染出配置行 —— 否则后面的断言可能是在"页面还没加载出来"上成立的。
  await screen.findByText('电话')
}

/** 取弹窗里某个 Form.Item 上的 Select（按标签键定位，与页面自身的 `label` 同源）。 */
function selectOf(dialog: HTMLElement, labelKey: string): HTMLElement {
  const label = within(dialog).getByText(labelKey)
  const item = label.closest('.ant-form-item')
  const select = item?.querySelector('.ant-select')
  // 自证锚点：取不到就说明选择器过期了，本条必须**红**而不是空过。
  expect(select).not.toBeNull()
  return select as HTMLElement
}

/**
 * 打开某个 antd Select 的下拉并返回它。
 *
 * <p>rc-select 在 `.ant-select-selector` 上监听 `mousedown` 来开合下拉（jsdom 里没有真实指针事件，
 * 这是驱动它的标准做法）。返回的是**最后一个未隐藏**的下拉：同页多个 Select 的下拉都挂在
 * document 上，关掉的那个带 `ant-select-dropdown-hidden`。
 */
function openDropdown(select: HTMLElement): HTMLElement {
  const selector = select.querySelector('.ant-select-selector')
  expect(selector).not.toBeNull()
  fireEvent.mouseDown(selector as Element)
  const dropdowns = document.querySelectorAll('.ant-select-dropdown:not(.ant-select-dropdown-hidden)')
  expect(dropdowns.length).toBeGreaterThan(0)
  return dropdowns[dropdowns.length - 1] as HTMLElement
}

/** 在下拉里点一个选项（选项文本由调用方给）。 */
function chooseOption(dropdown: HTMLElement, text: string) {
  fireEvent.click(within(dropdown).getByText(text))
}

/** 点工具栏「新增」并返回弹窗。 */
async function openCreate() {
  fireEvent.click(screen.getByRole('button', { name: /pages\.fieldPermission\.btnAdd/ }))
  return await screen.findByRole('dialog')
}

describe('FieldPermissionPage 的内置字段配置面（102 T17）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('列表的「字段」列渲染 fieldName（内置与自定义都是中文名），不再直出数字 id', async () => {
    await renderPage()

    expect(screen.getByText('电话')).toBeInTheDocument()
    expect(screen.getByText('客户等级')).toBeInTheDocument()
    // 数字 id 只在 `fieldName`/`fieldKey` 都缺失时才作为兜底出现（那是「字段定义已被删除」的证据）。
    // 这里两行都有名字 ⇒ 8842 不得出现在任何地方。
    expect(screen.queryByText('8842')).toBeNull()
  })

  it('字段下拉含内置与自定义两组，标签来自后端（内置项不是属性名 phone）', async () => {
    await renderPage()
    const svc = await import('../../services/fieldPermissionService')
    const dialog = await openCreate()

    // 选实体触发 `loadFields`（页面在实体 Select 的 onChange 里拉可配字段）
    chooseOption(
      openDropdown(selectOf(dialog, 'pages.fieldPermission.formEntityLabel')),
      'enums.entity.customer',
    )
    await waitFor(() => {
      expect(vi.mocked(svc.fetchAvailableFields)).toHaveBeenCalledWith('CUSTOMER')
    })

    const fieldDropdown = openDropdown(selectOf(dialog, 'pages.fieldPermission.formFieldLabel'))
    // 两组分组标题（102 新增的两个 i18n 键）+ 两组各自的选项
    expect(within(fieldDropdown).getByText('pages.fieldPermission.groupBuiltin')).toBeInTheDocument()
    expect(within(fieldDropdown).getByText('pages.fieldPermission.groupCustom')).toBeInTheDocument()
    expect(within(fieldDropdown).getByText('电话')).toBeInTheDocument()
    expect(within(fieldDropdown).getByText('客户等级')).toBeInTheDocument()
    // 内置项显示的是后端给的中文名，不是属性名 —— 属性名只出现在**请求体**里（见下一条）
    expect(within(fieldDropdown).queryByText('phone')).toBeNull()
  })

  it('提交内置字段时请求体带 fieldKey（fieldId 为 null），而不是数字 id', async () => {
    await renderPage()
    const dialog = await openCreate()

    chooseOption(
      openDropdown(selectOf(dialog, 'pages.fieldPermission.formEntityLabel')),
      'enums.entity.customer',
    )
    await waitFor(() => {
      expect(selectOf(dialog, 'pages.fieldPermission.formFieldLabel')).toBeInTheDocument()
    })
    chooseOption(
      openDropdown(selectOf(dialog, 'pages.fieldPermission.formFieldLabel')),
      '电话',
    )
    // 角色是必填项，不选则 validateFields 会拦住提交（那样断言会以"没调用"假绿）
    chooseOption(
      openDropdown(selectOf(dialog, 'pages.fieldPermission.formRoleLabel')),
      '销售代表',
    )

    fireEvent.click(within(dialog).getByRole('button', { name: /pages\.fieldPermission\.btnSave/ }))

    const svc = await import('../../services/fieldPermissionService')
    await waitFor(() => {
      expect(vi.mocked(svc.upsertFieldPermission)).toHaveBeenCalledWith({
        roleCode: 'SALES',
        entityType: 'CUSTOMER',
        // 权限项有 initialValue="READ_ONLY"，本用例不改它 ⇒ 恰好钉住"没动过的默认值也照常提交"
        permission: 'READ_ONLY',
        fieldId: null,
        fieldKey: 'phone',
      })
    })
  })
})
