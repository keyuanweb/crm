import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import ScheduledExportListPage from './ScheduledExportListPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/api/scheduledExportApi', () => ({
  scheduledExportApi: { list: vi.fn(), delete: vi.fn(), updateStatus: vi.fn() },
}))

/**
 * 086 权限收口 · `ScheduledExportListPage` 的**一码三控件 + 嵌套三元**的渲染测试。
 *
 * <pre>
 *   暂停（status=ACTIVE）    ← export:scheduled
 *   恢复（status=SUSPENDED） ← export:scheduled
 *   删除                     ← export:scheduled
 * </pre>
 *
 * <p>暂停/恢复是一个**嵌套三元**：
 * <pre>
 *   can[export:scheduled] ? ( row.status==='ACTIVE' ? 暂停 : row.status==='SUSPENDED' ? 恢复 : null ) : null
 * </pre>
 * 外层那一圈权限判据把整组包住——一旦漏掉它，两个状态分支都会无条件露出。所以表格里同时放
 * 一行 ACTIVE、一行 SUSPENDED，两个分支各断言一次，并且**在无码用户下两者都必须为 0**：
 * 只测其中一个分支的话，"漏了外层判据但恰好那一行状态不对"会假绿。
 *
 * <p>负向用例**必须**用非 ADMIN：`hasPerm` 对 `role === 'ADMIN'` 直接短路
 * （`hooks/usePermission.ts:10`），管理员永远拿不到"看不见"的结论。
 */
const activeRow = {
  id: 10,
  userId: 1,
  entityType: 'CUSTOMER',
  exportFormat: 'CSV',
  status: 'ACTIVE',
  nextExecutionTime: '2026-01-01T00:00:00',
  createdAt: '2026-01-01T00:00:00',
}
const suspendedRow = { ...activeRow, id: 11, status: 'SUSPENDED' }

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 只多了 `export:scheduled`，其余逐字相同。 */
const salesWithPerm: UserInfo = { ...salesNoPerm, permissions: ['export:scheduled'] }

const queryPause = () => screen.queryAllByText('pages.scheduledExport.list.pause')
const queryResume = () => screen.queryAllByText('pages.scheduledExport.list.resume')
const queryDelete = () => screen.queryAllByText('common.button.delete')

async function renderPage(user: UserInfo) {
  useAuthStore.setState({ user })
  const { scheduledExportApi } = await import('../../services/api/scheduledExportApi')
  vi.mocked(scheduledExportApi.list).mockResolvedValue([activeRow, suspendedRow] as never)

  renderWithProviders(<ScheduledExportListPage />)
  // 等到表格真的渲染出两行任务，后续的否定断言才有意义 ——
  // 否则"按钮不在"可能只是"列表还没拉回来"，那样的负向断言是假绿。
  await screen.findByText('pages.scheduledExport.list.title')
  await screen.findByText('pages.scheduledExport.common.taskStatusLabels.SUSPENDED')
}

/** 反空洞守卫：两行任务与它们的状态标签都真的落在表格里了。 */
function expectRowsRendered() {
  expect(screen.getByText('pages.scheduledExport.list.title')).toBeInTheDocument()
  expect(screen.getByText('pages.scheduledExport.common.taskStatusLabels.ACTIVE')).toBeInTheDocument()
  expect(screen.getByText('pages.scheduledExport.common.taskStatusLabels.SUSPENDED')).toBeInTheDocument()
  // 未收口的「执行历史」链接仍在（防止把关权限时把整列包进去）
  expect(screen.getAllByText('pages.scheduledExport.common.executionHistory')).toHaveLength(2)
}

describe('ScheduledExportListPage 权限收口（086）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('ADMIN 看得见「暂停」「恢复」「删除」', async () => {
    await renderPage(adminUser)

    expectRowsRendered()
    expect(queryPause()).toHaveLength(1) // 只有 ACTIVE 行
    expect(queryResume()).toHaveLength(1) // 只有 SUSPENDED 行
    expect(queryDelete()).toHaveLength(2)
  })

  it('无 export:scheduled 的 SALES：两行都渲染了，但「暂停」「恢复」「删除」都不在', async () => {
    await renderPage(salesNoPerm)

    expectRowsRendered()
    expect(queryPause()).toHaveLength(0)
    expect(queryResume()).toHaveLength(0)
    expect(queryDelete()).toHaveLength(0)
  })

  it('持有 export:scheduled 的 SALES：看得见「暂停」「恢复」「删除」（该权限从此可授予）', async () => {
    await renderPage(salesWithPerm)

    expectRowsRendered()
    expect(queryPause()).toHaveLength(1)
    expect(queryResume()).toHaveLength(1)
    expect(queryDelete()).toHaveLength(2)
  })
})
