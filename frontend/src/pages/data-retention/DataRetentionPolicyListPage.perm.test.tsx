import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import DataRetentionPolicyListPage from './DataRetentionPolicyListPage'
import { useAuthStore } from '../../store/authStore'
import type { UserInfo } from '../../store/authStore'

vi.mock('../../services/api/dataRetentionApi', () => ({
  dataRetentionApi: { getAllPolicies: vi.fn(), deletePolicy: vi.fn() },
}))

/**
 * 086 权限收口 · `DataRetentionPolicyListPage` 行内「删除」按钮的渲染测试。
 *
 * <p>判据是 `retention:delete`（`DELETE /data-retention/policies/{id}`，
 * DataRetentionPolicyController.java:66-67）。
 *
 * <p><b>刻意留白的一处</b>：工具栏的「合规导出」按钮不收口——它只 `navigate()` 到
 * `/data-retention/compliance-export`，**不发任何请求**，没有可对应的端点与权限码。
 * 所以本文件对它不写任何断言（既不写"无码可见"也不写"无码不可见"），
 * 免得不存在的约束被测试固化成"规范"。
 *
 * <p>负向用例**必须**用非 ADMIN：`hasPerm` 对 `role === 'ADMIN'` 直接短路
 * （`hooks/usePermission.ts:10`），管理员永远拿不到"看不见"的结论。
 */
const policyRow = {
  id: 7,
  entityType: 'CUSTOMER',
  retentionDays: 30,
  actionType: 'ARCHIVE',
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00',
}

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }
/** 非 ADMIN 基线：零权限码。 */
const salesNoPerm: UserInfo = { id: 2, username: 'sales01', displayName: '销售一', role: 'SALES', permissions: [] }
/** 只多了 `retention:delete`，其余逐字相同。 */
const salesWithPerm: UserInfo = { ...salesNoPerm, permissions: ['retention:delete'] }

const queryDelete = () => screen.queryAllByText('common.button.delete')

async function renderPage(user: UserInfo) {
  useAuthStore.setState({ user })
  const { dataRetentionApi } = await import('../../services/api/dataRetentionApi')
  vi.mocked(dataRetentionApi.getAllPolicies).mockResolvedValue([policyRow] as never)

  renderWithProviders(<DataRetentionPolicyListPage />)
  // 等到表格真的渲染出这一行策略，后续的否定断言才有意义 ——
  // 否则"按钮不在"可能只是"列表还没拉回来"，那样的负向断言是假绿。
  await screen.findByText('pages.dataRetention.common.entityTypeLabels.CUSTOMER')
}

/** 反空洞守卫：策略行与未收口的「执行历史」链接都真的渲染了。 */
function expectRowRendered() {
  expect(screen.getByText('pages.dataRetention.common.entityTypeLabels.CUSTOMER')).toBeInTheDocument()
  expect(screen.getByText('pages.dataRetention.common.policyStatusLabels.ACTIVE')).toBeInTheDocument()
  expect(screen.getByText('pages.dataRetention.common.executionHistory')).toBeInTheDocument()
  expect(screen.getByText('common.button.edit')).toBeInTheDocument()
}

describe('DataRetentionPolicyListPage 权限收口（086）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('ADMIN 看得见行内「删除」', async () => {
    await renderPage(adminUser)

    expectRowRendered()
    expect(queryDelete()).toHaveLength(1)
  })

  it('无 retention:delete 的 SALES：行渲染出来了，但「删除」不在', async () => {
    await renderPage(salesNoPerm)

    expectRowRendered()
    expect(queryDelete()).toHaveLength(0)
  })

  it('持有 retention:delete 的 SALES：看得见「删除」（该权限从此可授予）', async () => {
    await renderPage(salesWithPerm)

    expectRowRendered()
    expect(queryDelete()).toHaveLength(1)
  })
})
