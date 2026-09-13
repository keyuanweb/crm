import { describe, expect, it, vi, beforeEach } from 'vitest'
import { act, fireEvent, screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import DuplicateMergePage from './DuplicateMergePage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/customerMergeService', () => ({
  fetchDuplicates: vi.fn(),
  mergeCustomers: vi.fn(),
}))

/**
 * 086 权限收口 · `DuplicateMergePage`「扫描 / 合并」的渲染测试。
 *
 * <p>本页**两个动作共用一个码**（`CustomerMergeController` 的 `GET /customers/duplicates` 与
 * `POST /customers/merge` 同挂 `customer:merge`），所以判据只有一条，两个控件一起收。
 *
 * <p>陷阱在于**两个控件的出现条件不同**：「扫描」是常驻工具栏按钮，「合并」长在
 * **扫描结果的行**上——没扫描过就根本不存在。于是「合并按钮不在」有两种成因
 * （没权限 / 压根没扫出结果来），单看一次渲染分不清，那样的负向断言是假绿。
 *
 * <p>破解办法是让**同一份扫描结果留在屏上**、只换权限：先以持码用户扫描出分组，
 * 断言「合并」可见（③ 码生效），再在组件树不卸载的前提下把用户换成无码用户，
 * 断言分组**仍在**而「合并」消失（②）。此时"不在"只可能由权限引起。
 *
 * <p>负向用例**必须**用非 ADMIN：`hasPerm` 对 `role === 'ADMIN'` 直通返回 true
 * （`hooks/usePermission.ts:10`），管理员永远测不出「看不见」。
 */
const group = {
  id: 1,
  primaryId: 10,
  primaryName: '主客户甲',
  duplicates: [
    { customerId: 11, name: '重复客户乙', company: '乙公司', similarity: 95, relatedCount: 3 },
  ],
}

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码——"看不见"的对照组必须建立在它之上。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 只多了 `customer:merge`，其余与基线逐字相同——把差异收敛到唯一变量。 */
const salesWithMerge: UserInfo = { ...salesNoPerm, permissions: ['customer:merge'] }

const withUser = (user: UserInfo) => useAuthStore.setState({ user })

const queryScan = () => screen.queryByRole('button', { name: /pages\.duplicateMerge\.btnScan/ })
const queryMerge = () => screen.queryByRole('button', { name: /pages\.duplicateMerge\.btnMerge/ })

async function renderPage(user: UserInfo) {
  withUser(user)
  const { fetchDuplicates } = await import('../../services/customerMergeService')
  vi.mocked(fetchDuplicates).mockResolvedValue([group] as never)
  renderWithProviders(<DuplicateMergePage />)
  // 页面外壳（提示语与合并规则）与权限无关，先等它出来，"扫描按钮不在"才不是白屏假绿
  await screen.findByText('pages.duplicateMerge.scanHint')
}

/** 点「扫描」并等到分组卡片渲染出来（分组名 + 组内重复客户名是页面上最实在的内容）。 */
async function scan() {
  fireEvent.click(screen.getByRole('button', { name: /pages\.duplicateMerge\.btnScan/ }))
  await screen.findByText('主客户甲')
}

describe('DuplicateMergePage 扫描 / 合并的权限收口（086）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('ADMIN 看得见「扫描」，扫描后「合并」也看得见', async () => {
    await renderPage(adminUser)

    expect(queryScan()).toBeInTheDocument()
    await scan()
    expect(queryMerge()).toBeInTheDocument()
  })

  it('零权限码的 SALES 看不见「扫描」（页面本身渲染正常）', async () => {
    await renderPage(salesNoPerm)

    // 反空洞守卫：先证明页面确实渲染出了与权限无关的真实内容
    expect(screen.getByText('pages.duplicateMerge.mergeRule')).toBeInTheDocument()
    expect(queryScan()).not.toBeInTheDocument()
  })

  it('持 customer:merge 的 SALES 看得见「扫描」，扫描后看得见「合并」', async () => {
    await renderPage(salesWithMerge)

    expect(queryScan()).toBeInTheDocument()
    await scan()
    expect(queryMerge()).toBeInTheDocument()
  })

  it('同一份扫描结果下换成无码用户：「合并」随即消失（证明消失的唯一变量是权限）', async () => {
    await renderPage(salesWithMerge)
    await scan()
    // ③ 前置：此刻「合并」确实长在这一行上（否则下面的消失与权限无关）
    expect(queryMerge()).toBeInTheDocument()

    // 只换用户，不卸载组件树——分组数据来自本页 state，与用户无关，必须原地留存
    await act(async () => {
      useAuthStore.setState({ user: salesNoPerm })
    })

    // 反空洞守卫：分组内容仍在屏上，说明"合并按钮不在"不是"页面空了"
    expect(screen.getByText('主客户甲')).toBeInTheDocument()
    expect(screen.getByText('重复客户乙')).toBeInTheDocument()
    expect(queryMerge()).not.toBeInTheDocument()
    expect(queryScan()).not.toBeInTheDocument()
  })
})
