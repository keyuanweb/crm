import { describe, expect, it, vi, beforeEach } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { renderWithProviders } from '../../test/renderWithProviders'
import DataRetentionExecutionHistoryPage from './DataRetentionExecutionHistoryPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/api/dataRetentionApi', () => ({
  dataRetentionApi: {
    getPolicy: vi.fn(),
    getExecutions: vi.fn(),
    executeArchival: vi.fn(),
  },
}))

/**
 * 086 权限收口 · `DataRetentionExecutionHistoryPage` 工具栏「立即执行」的渲染测试。
 *
 * <p>判据是 `retention:execute`（`DataRetentionPolicyController.java:78-79` 的
 * `POST /data-retention/execute`）。本页<b>只有这一个</b>被收口的控件：工具栏的「返回」只
 * `navigate()`、不发请求（`DataRetentionExecutionHistoryPage.tsx:95`），
 * 三个读端点（`/policies/{id}`、`/policies/{id}/executions`）后端<b>刻意不挂码</b>
 * （FR-G14：字典里没有对应的读权限码），故不收口。
 *
 * <p>本页是 `useParams` 详情页：必须自己声明 `<Routes>` 把 `:id` 喂进去，
 * 否则 `id === undefined` ⇒ `loadDetail` 直接 return ⇒ 页面永远停在 `Loading...`，
 * 那样后续所有"按钮不在"的断言都是假绿。
 *
 * <p>负向用例<b>必须</b>用非 ADMIN：`hasPerm` 对 `role === 'ADMIN'` 直接短路
 * （`hooks/usePermission.ts:10`），管理员永远拿不到"看不见"的结论。
 */
const policy = {
  id: 7,
  entityType: 'CUSTOMER',
  retentionDays: 30,
  actionType: 'ARCHIVE',
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00',
}

const execution = {
  id: 1,
  policyId: 7,
  executedAt: '2026-09-01T02:00:00',
  status: 'SUCCESS',
  processedCount: 12,
}

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码。负向用例必须建立在它之上（ADMIN 在 hasPerm 里直通）。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 只多一个码，其余与基线逐字相同——把差异收敛到唯一变量。 */
const salesWithPerm: UserInfo = { ...salesNoPerm, permissions: ['retention:execute'] }
/** 同族里的**另一个**码：证明确实取的是 `execute`，不是把 retention 家族里随便哪个码当成了闸门。 */
const salesWithDelete: UserInfo = { ...salesNoPerm, permissions: ['retention:delete'] }

const executeButton = () => screen.queryByRole('button', { name: /pages\.dataRetention\.history\.executeNow/ })
const backButton = () => screen.queryByRole('button', { name: /pages\.dataRetention\.common\.back/ })

/**
 * 反空洞守卫：页面**真实内容**已渲染。
 * 「返回」按钮不受任何权限管辖、策略信息与执行记录都由已 mock 的响应渲染而来，
 * 它们出现 ⇒ policy 已加载、页面真的渲染出来了，后续的"按钮不在"才是真结论。
 */
function expectPageRendered() {
  expect(backButton()).toBeInTheDocument()
  // 实体类型标签（策略信息里的 Descriptions）——由 policy.entityType 查表渲染
  expect(screen.getByText('pages.dataRetention.common.entityTypeLabels.CUSTOMER')).toBeInTheDocument()
  // 归档方式标签
  expect(screen.getByText('pages.dataRetention.common.actionTypeLabels.ARCHIVE')).toBeInTheDocument()
  // 执行记录行：状态 Tag + 处理数量（证明 List 真的有数据，不是空态）
  expect(screen.getByText('pages.dataRetention.common.executionStatusLabels.SUCCESS')).toBeInTheDocument()
  expect(screen.getByText('pages.dataRetention.common.processedCountValue')).toBeInTheDocument()
}

async function renderPage(user: UserInfo) {
  useAuthStore.setState({ user })
  const { dataRetentionApi } = await import('../../services/api/dataRetentionApi')
  vi.mocked(dataRetentionApi.getPolicy).mockResolvedValue(policy as never)
  vi.mocked(dataRetentionApi.getExecutions).mockResolvedValue([execution] as never)

  renderWithProviders(
    <Routes>
      <Route path="/data-retention/:id/executions" element={<DataRetentionExecutionHistoryPage />} />
    </Routes>,
    { route: '/data-retention/7/executions' },
  )

  // 反空洞守卫的入口：页面对 `!policy || loading` 会渲染成 Loading...，必须先等真内容出来。
  await screen.findByRole('button', { name: /pages\.dataRetention\.common\.back/ }, { timeout: 5000 })
}

describe('DataRetentionExecutionHistoryPage 收口（086）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('① ADMIN：「立即执行」可见（收口未误伤管理员）', async () => {
    await renderPage(adminUser)

    expectPageRendered()
    expect(executeButton()).toBeInTheDocument()
  })

  it('② 无 retention:execute 的 SALES：页面照常渲染，但「立即执行」不在', async () => {
    await renderPage(salesNoPerm)

    expectPageRendered()
    expect(executeButton()).not.toBeInTheDocument()
  })

  it('③ 持 retention:execute 的 SALES：「立即执行」可见（证明该码可授予）', async () => {
    await renderPage(salesWithPerm)

    expectPageRendered()
    expect(executeButton()).toBeInTheDocument()
  })

  it('④ 只持同族 retention:delete 的 SALES：「立即执行」仍不可见（锁住码的身份）', async () => {
    await renderPage(salesWithDelete)

    expectPageRendered()
    expect(executeButton()).not.toBeInTheDocument()
  })

  it('⑤ 持 retention:execute 的 SALES：点「立即执行」真的打的是 executeArchival', async () => {
    await renderPage(salesWithPerm)
    const { dataRetentionApi } = await import('../../services/api/dataRetentionApi')

    fireEvent.click(executeButton() as HTMLElement)

    // 被门控的那个控件确实是 POST /data-retention/execute 的入口，不是个摆设按钮
    await waitFor(() => expect(dataRetentionApi.executeArchival).toHaveBeenCalledTimes(1))
  })
})
