import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import RecycleBinPage from './RecycleBinPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/recycleService', () => ({
  fetchRecycleBin: vi.fn(),
  restoreItems: vi.fn(),
  purgeItems: vi.fn(),
}))

/**
 * 086 权限收口 · `RecycleBinPage` 的**两个码**渲染测试。
 *
 * <p>本页的特点是同一条工具栏上挂着**两个不同的码**（`RecycleBinController` 里
 * `POST /recycle-bin/restore` 与 `POST /recycle-bin/purge` 各挂各的），所以不可一刀切：
 * 「恢复」按 `recycle:restore`、「彻底删除」按 `recycle:purge`。
 *
 * <p>因此每个按钮各要过三条：ADMIN 可见 / 无码非 ADMIN 不可见 / 只有该码的非 ADMIN 可见。
 * 后一条（③）才是证明「码本身起了作用」的那一条——没有它，「按钮不在」可能只是页面没渲染。
 *
 * <p>另加两条**交叉断言**（只持 `recycle:restore` 的人看不见「彻底删除」、反之亦然）：
 * 若哪天图省事把两个判据合并成一个码，这两条会立刻红。而「彻底删除」是不可逆操作，
 * 判据合并正是本页最该防的退化。
 *
 * <p>负向用例**必须**用非 ADMIN：`hasPerm` 对 `role === 'ADMIN'` 直通返回 true
 * （`hooks/usePermission.ts:10`），管理员永远测不出「看不见」。
 */
const item = { type: 'CUSTOMER' as const, id: 1, name: '已删客户甲', deletedAt: '2026-01-02T03:04:05', deletedBy: 7 }

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码——"看不见"的对照组必须建立在它之上。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 只多了 `recycle:restore`，其余与基线逐字相同——把差异收敛到唯一变量。 */
const salesRestoreOnly: UserInfo = { ...salesNoPerm, permissions: ['recycle:restore'] }
/** 只多了 `recycle:purge`。 */
const salesPurgeOnly: UserInfo = { ...salesNoPerm, permissions: ['recycle:purge'] }

const withUser = (user: UserInfo) => useAuthStore.setState({ user })

/** 工具栏「恢复」按钮（Popconfirm 包着，但按钮本体按 role+name 定位最稳）。 */
const queryRestore = () => screen.queryByRole('button', { name: /pages\.recycleBin\.btnRestore/ })
/** 工具栏「彻底删除」按钮。 */
const queryPurge = () => screen.queryByRole('button', { name: /pages\.recycleBin\.btnDelete/ })

async function renderPage(user: UserInfo) {
  withUser(user)
  const { fetchRecycleBin } = await import('../../services/recycleService')
  vi.mocked(fetchRecycleBin).mockResolvedValue({ items: [item], total: 1, page: 1, pageSize: 20 } as never)
  renderWithProviders(<RecycleBinPage />)
  // 等到表格真的渲染出这一行，后续的否定断言才有意义 ——
  // 否则"按钮不在"可能只是"页面还没加载出来"，那样的负向断言是假绿。
  await screen.findByText('已删客户甲')
}

describe('RecycleBinPage 两个按钮的权限收口（086）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('ADMIN 两个按钮都看得见', async () => {
    await renderPage(adminUser)

    expect(queryRestore()).toBeInTheDocument()
    expect(queryPurge()).toBeInTheDocument()
  })

  it('零权限码的 SALES 两个按钮都看不见（页面本身渲染正常）', async () => {
    await renderPage(salesNoPerm)

    // 反空洞守卫：先证明页面确实渲染出了那一行，否定断言才不是"白屏也算过"
    expect(screen.getByText('已删客户甲')).toBeInTheDocument()
    expect(queryRestore()).not.toBeInTheDocument()
    expect(queryPurge()).not.toBeInTheDocument()
  })

  it('只持 recycle:restore 的 SALES：看得见「恢复」，看不见「彻底删除」', async () => {
    await renderPage(salesRestoreOnly)

    // ③ 这一条证明该码真的能授予（改造前只有 ADMIN 硬编码时它永远授不出去）
    expect(queryRestore()).toBeInTheDocument()
    // 交叉断言：两码不得互相放行，尤其不可逆的 purge
    expect(queryPurge()).not.toBeInTheDocument()
  })

  it('只持 recycle:purge 的 SALES：看得见「彻底删除」，看不见「恢复」', async () => {
    await renderPage(salesPurgeOnly)

    expect(queryPurge()).toBeInTheDocument()
    expect(queryRestore()).not.toBeInTheDocument()
  })
})
