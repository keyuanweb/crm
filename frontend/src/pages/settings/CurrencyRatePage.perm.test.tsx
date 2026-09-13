import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen, within } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import CurrencyRatePage from './CurrencyRatePage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/currencyService', () => ({
  fetchCurrencies: vi.fn(),
  createCurrency: vi.fn(),
  updateCurrency: vi.fn(),
  deleteCurrency: vi.fn(),
}))

/**
 * 086 权限收口 · `CurrencyRatePage` 行内「删除」的渲染测试。
 *
 * <p>被测控件：action 列里包在 `Popconfirm` 内的行内「删除」链接（`CurrencyRatePage.tsx:57-61`）。
 * 判据是 `can[PERMS.currencyManage]`，码为 `currency:manage`（`constants/permissions.ts:244`）——
 * 与页面注释（`:32`）一致。该码经后端核对无误：删除汇率打
 * `DELETE /api/v1/currencies/{id}`，`CurrencyRateController.java:69-70` 上挂的正是
 * `@RequirePermission("currency:manage")`。
 *
 * <p>本页的删除位外面还叠了一层**业务状态三元**（`:50`）：基准币行渲染的是灰色
 * `<span>`占位（`pages.currency.baseNotEditable`），既没有「编辑」也没有「删除」。
 * 这与权限无关，故本文件把它当**反空洞守卫**用而不是当收口断言：
 * 第 2 例（无码）里它照样在，证明权限判据只嵌在非基准那一支里，没把状态分支一起吞掉。
 *
 * <p>同一 action 列里**不受权限管辖**的兄弟控件是「编辑」（`:54`，无判据包裹）——
 * 它是"该列确实渲染过"的证据，缺了它，"删除不在"就可能是整页没渲染出来的假绿。
 *
 * <p>负向用例**必须**用非 ADMIN：`hasPerm` 对 `role === 'ADMIN'` 直通返回 true
 * （`hooks/usePermission.ts:10`），拿管理员永远测不出「看不见」。
 */
const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码——"看不见"的对照组必须建立在它之上。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 只多了 `currency:manage`，其余与基线逐字相同——把差异收敛到唯一变量。 */
const salesWithPerm: UserInfo = { ...salesNoPerm, permissions: ['currency:manage'] }

/** 基准币：永远是灰色占位，不可编辑、不可删——与权限无关。 */
const baseRow = { id: 1, code: 'CNY', name: '人民币', rate: 1, isBase: true, enabled: true, version: 0 }
/** 非基准币：action 列真的渲染「编辑」与「删除」。 */
const usdRow = { id: 2, code: 'USD', name: '美元', rate: 7.1, isBase: false, enabled: true, version: 0 }

const LBL_DELETE = 'pages.currency.btnDelete'
const LBL_EDIT = 'pages.currency.btnEdit'
const LBL_BASE_NOT_EDITABLE = 'pages.currency.baseNotEditable'

/** 按币种名单元格定位到 `<tr>`——两行的 action 列内容不同，行内查询才分得清。 */
function rowOf(renderedName: string): HTMLElement {
  const tr = screen.getByText(renderedName).closest('tr')
  if (!tr) throw new Error(`未找到「${renderedName}」所在的行`)
  return tr as HTMLElement
}

async function renderPage(user: UserInfo) {
  useAuthStore.setState({ user })
  const { fetchCurrencies } = await import('../../services/currencyService')
  vi.mocked(fetchCurrencies).mockResolvedValue([baseRow, usdRow] as never)

  renderWithProviders(<CurrencyRatePage />)
  // 反空洞守卫：两行都渲染出来（币种名由行数据渲染而来），否定断言才有意义 ——
  // 否则"按钮不在"可能只是"页面还没加载出来"，那样的负向断言是假绿。
  await screen.findByText('美元')
  await screen.findByText('人民币')
}

describe('CurrencyRatePage 行内删除的权限收口（086）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('ADMIN 看得见非基准币行内「删除」；基准币行仍是灰色占位（状态分支，与权限无关）', async () => {
    await renderPage(adminUser)

    const usd = rowOf('美元')
    const base = rowOf('人民币')

    expect(within(usd).queryByText(LBL_EDIT)).toBeInTheDocument()
    expect(within(usd).queryByText(LBL_DELETE)).toBeInTheDocument()
    // 基准币：占位在、两个动作都不在——这条锁的是状态判据本身
    expect(within(base).queryByText(LBL_BASE_NOT_EDITABLE)).toBeInTheDocument()
    expect(within(base).queryByText(LBL_EDIT)).not.toBeInTheDocument()
    expect(within(base).queryByText(LBL_DELETE)).not.toBeInTheDocument()
  })

  it('无 currency:manage 的 SALES 看不见「删除」（同列「编辑」与基准占位照常渲染）', async () => {
    await renderPage(salesNoPerm)

    const usd = rowOf('美元')
    const base = rowOf('人民币')

    // 反空洞守卫：数据行在，同一 action 列里不受权限管辖的「编辑」也在——
    // 证明列渲染过了，不见的确实只是那个受权限管辖的控件。
    expect(screen.getByText('美元')).toBeInTheDocument()
    expect(within(usd).queryByText(LBL_EDIT)).toBeInTheDocument()
    // 权限造成的不可见：元素根本不存在
    expect(within(usd).queryByText(LBL_DELETE)).not.toBeInTheDocument()
    // 状态造成的不可见：与权限无关的灰色占位，无码时照样在
    expect(within(base).queryByText(LBL_BASE_NOT_EDITABLE)).toBeInTheDocument()
  })

  it('持有 currency:manage 的 SALES 看得见「删除」（证明该码可授予，而非 ADMIN 特权）', async () => {
    await renderPage(salesWithPerm)

    const usd = rowOf('美元')

    expect(within(usd).queryByText(LBL_EDIT)).toBeInTheDocument()
    expect(within(usd).queryByText(LBL_DELETE)).toBeInTheDocument()
  })
})
