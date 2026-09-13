import { describe, expect, it, vi, beforeEach } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { renderWithProviders } from '../../test/renderWithProviders'
import ScheduledExportExecutionHistoryPage from './ScheduledExportExecutionHistoryPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/api/scheduledExportApi', () => ({
  scheduledExportApi: {
    detail: vi.fn(),
    getExecutions: vi.fn(),
    executeNow: vi.fn(),
  },
}))

/**
 * 086 权限收口 · `ScheduledExportExecutionHistoryPage` 工具栏「立即执行」的**渲染层**双向用例。
 *
 * <p>判据落在 `ScheduledExportExecutionHistoryPage.tsx:104` 的
 * `{can[PERMS.exportScheduled] && ( … )}`，码为 `export:scheduled`
 * （`ScheduledExportController.java` 的 `POST /scheduled-exports/{id}/execute-now`）。
 *
 * <p><b>为什么这条用例此前是缺口：</b>086 的统计是按码字面量扫的，`export:scheduled` 在
 * `ScheduledExportListPage.perm.test.tsx` 的**负向夹具**里以字符串形式出现过（那一页用它
 * 断言暂停/恢复/删除不可见），于是被判为"已覆盖"。但同码在本页的落点（工具栏「立即执行」）
 * 那一行 `&&` **从未被执行过**——本页零用例。此文件把那处判据补成双向。
 *
 * <p><b>本页只有这一个**受码管辖**的控件：</b>
 * <pre>
 *   工具栏「返回」  ← 只 navigate()，不发请求，不受码管辖
 *   工具栏「刷新」  ← 只是重新拉取（loadDetail），不受码管辖（源码 :24 注释明示）
 *   工具栏「立即执行」← export:scheduled ✅ 就是 `:104` 这一行
 *   两种读端点（detail / executions）后端不挂码，故不收口
 * </pre>
 * 因此负向用例除断言「立即执行」不在之外，还必须断言「刷新」「返回」**仍在**：
 * 它们是同区域里不受该码管辖的相邻控件，证明工具栏确实渲染过、否定断言不是"整页没渲染"的假绿。
 *
 * <p><b>行姿态是固定的：</b>`:104` 的判据只有权限码一个条件（不像 ListPage 那处还嵌了
 * `row.status` 的三元），所以夹具只要让 `!task || loading` 的整页短路不成立即可——
 * 任务详情 mock 成固定一条 ACTIVE 任务，所有用例共用，唯一变量就是 `user.permissions`。
 *
 * <p><b>负向用例必须用非 ADMIN：</b>`hasPerm` 对 `role === 'ADMIN'` 直接短路为 `true`
 * （`hooks/usePermission.ts:10`），管理员永远拿不到"看不见"的结论——拿 ADMIN 跑负向，
 * 断言会恒真的反向失败或无意义。ADMIN 只出现在①的正向（"全可见"）用例里。
 *
 * <p>本页是 `useParams` 详情页：必须自己声明 `<Routes>` 把 `:id` 喂进去，否则 `id === undefined`
 * ⇒ `loadDetail` 直接 return ⇒ 页面永远停在 `Loading...`，那样所有"按钮不在"的断言都是假绿。
 * 路由路径照抄 `App.tsx:831` 的 `exports/scheduled/:id/executions`。
 */
const task = {
  id: 7,
  userId: 1,
  entityType: 'CUSTOMER',
  exportFormat: 'CSV',
  cronExpression: '0 0 2 * * *',
  status: 'ACTIVE',
  nextExecutionTime: '2026-10-01T02:00:00',
  createdAt: '2026-01-01T00:00:00',
}

/** 一条真实执行记录：保证 List 渲染的是数据行而非空态。 */
const execution = {
  id: 1,
  scheduledExportId: 7,
  executedAt: '2026-09-01T02:00:00',
  status: 'SUCCESS',
  rowCount: 12,
  fileSize: 204800,
}

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码。负向用例必须建立在它之上（ADMIN 在 hasPerm 里直通）。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 只多一个码，其余与基线逐字相同——把差异收敛到唯一变量。 */
const salesWithPerm: UserInfo = { ...salesNoPerm, permissions: ['export:scheduled'] }
/** 同族里的**另一个**码：证明确实取的是 `export:scheduled`，不是把 export 家族里随便哪个码当了闸门。 */
const salesWithOtherExportPerm: UserInfo = { ...salesNoPerm, permissions: ['export:create'] }

const executeNowButton = () => screen.queryByRole('button', { name: /pages\.scheduledExport\.history\.executeNow/ })
const refreshButton = () => screen.queryByRole('button', { name: /pages\.scheduledExport\.history\.refresh/ })
const backButton = () => screen.queryByRole('button', { name: /pages\.scheduledExport\.common\.back/ })

/**
 * 反空洞守卫：页面**真实内容**已渲染。
 * 三个锚点各自独立于被测判据：页标题（Space 里恒在的 Title）、卡片标题（task 已加载）、
 * 执行记录行（executions 非空 + 行内 `rowCountValue`），以及两个不受码管辖的工具栏按钮。
 * 它们出现 ⇒ task 已返回、页面真的渲染出来了，后续"立即执行不在"才是真结论。
 */
function expectPageRendered() {
  // 锚点 1：页标题（由 task.entityType 查表插值，恒存在）
  expect(screen.getByText('pages.scheduledExport.common.historyTitle')).toBeInTheDocument()
  // 锚点 2：任务信息卡片标题 —— 只有 `!task || loading` 的短路不成立才会出现
  expect(screen.getByText('pages.scheduledExport.history.taskInfo')).toBeInTheDocument()
  // 锚点 3：卡片正文由已 mock 的 task 渲染而来
  expect(screen.getByText('pages.scheduledExport.common.entityTypeLabels.CUSTOMER')).toBeInTheDocument()
  expect(screen.getByText('pages.scheduledExport.common.exportFormatLabels.CSV')).toBeInTheDocument()
  expect(screen.getByText('pages.scheduledExport.common.taskStatusLabels.ACTIVE')).toBeInTheDocument()
  // 锚点 4：历史列表真的有数据（不是空态）
  expect(screen.getByText('pages.scheduledExport.common.executionStatusLabels.SUCCESS')).toBeInTheDocument()
  expect(screen.getByText('pages.scheduledExport.common.rowCountValue')).toBeInTheDocument()
  // 锚点 5：同区域内**不受该码管辖**的相邻控件仍在
  expect(backButton()).toBeInTheDocument()
  expect(refreshButton()).toBeInTheDocument()
}

async function renderPage(user: UserInfo) {
  useAuthStore.setState({ user })
  const { scheduledExportApi } = await import('../../services/api/scheduledExportApi')
  vi.mocked(scheduledExportApi.detail).mockResolvedValue(task as never)
  vi.mocked(scheduledExportApi.getExecutions).mockResolvedValue([execution] as never)

  renderWithProviders(
    <Routes>
      <Route path="/exports/scheduled/:id/executions" element={<ScheduledExportExecutionHistoryPage />} />
    </Routes>,
    { route: '/exports/scheduled/7/executions' },
  )

  // 入口守卫：`!task || loading` 时整页渲染成 `Loading...`，必须先等真内容出来。
  await screen.findByText('pages.scheduledExport.history.taskInfo', undefined, { timeout: 5000 })
}

describe('ScheduledExportExecutionHistoryPage 权限收口（087 回填）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('① ADMIN：「立即执行」可见（收口未误伤管理员）', async () => {
    await renderPage(adminUser)

    expectPageRendered()
    expect(executeNowButton()).toBeInTheDocument()
  })

  it('② 无 export:scheduled 的 SALES：页面照常渲染，「立即执行」不在，「刷新」「返回」仍在', async () => {
    await renderPage(salesNoPerm)

    expectPageRendered()
    expect(executeNowButton()).not.toBeInTheDocument()
  })

  it('③ 持 export:scheduled 的 SALES：「立即执行」可见（证明该码可授予）', async () => {
    await renderPage(salesWithPerm)

    expectPageRendered()
    expect(executeNowButton()).toBeInTheDocument()
  })

  it('④ 只持同族 export:create 的 SALES：「立即执行」仍不可见（锁住码的身份）', async () => {
    await renderPage(salesWithOtherExportPerm)

    expectPageRendered()
    expect(executeNowButton()).not.toBeInTheDocument()
  })

  it('⑤ 持 export:scheduled 的 SALES：点「立即执行」真的打的是 executeNow', async () => {
    await renderPage(salesWithPerm)
    const { scheduledExportApi } = await import('../../services/api/scheduledExportApi')

    fireEvent.click(executeNowButton() as HTMLElement)

    // 被门控的那个控件确实是 POST /scheduled-exports/{id}/execute-now 的入口，不是个摆设按钮
    await waitFor(() => expect(scheduledExportApi.executeNow).toHaveBeenCalledWith(7))
  })
})
