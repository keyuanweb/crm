import { beforeEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import AuditLogPage from './AuditLogPage'
import { fetchAuditLogs } from '../../services/auditLogService'
import type { AuditLog } from '../../types/auditLog'

vi.mock('../../services/auditLogService', () => ({
  fetchAuditLogs: vi.fn(),
}))

describe('AuditLogPage（T071，antd ProTable 版）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(fetchAuditLogs).mockResolvedValue({
      items: [],
      total: 0,
      page: 1,
      pageSize: 20,
    })
  })

  it('渲染标题与空状态', async () => {
    renderWithProviders(<AuditLogPage />)
    expect(await screen.findByText(/pages\.auditLog\.title/, {}, { timeout: 5000 })).toBeInTheDocument()
    // ProTable 在 antd zhCN locale 下显示"暂无数据"（可能出现在 title 标签中）
    await waitFor(
      () => expect(screen.queryAllByText('暂无数据').length).toBeGreaterThan(1),
      { timeout: 5000 },
    )
  })

  it('渲染审计记录行', async () => {
    const row: AuditLog = {
      id: 1,
      actorName: 'admin',
      action: 'CREATE',
      entityType: 'CUSTOMER',
      entityId: 42,
      detail: '创建客户：张三',
      createdAt: '2026-08-22T09:00:00',
    }
    vi.mocked(fetchAuditLogs).mockResolvedValue({
      items: [row],
      total: 1,
      page: 1,
      pageSize: 20,
    })
    renderWithProviders(<AuditLogPage />)
    const table = await screen.findByRole('table')
    await waitFor(() => expect(within(table).getByText('admin')).toBeInTheDocument())
    // action 和 entity 使用 i18n key
    expect(within(table).getByText(/pages\.auditLog\.actionCreate/)).toBeInTheDocument()
    expect(within(table).getByText(/pages\.auditLog\.entityCustomer/)).toBeInTheDocument()
    expect(within(table).getByText('42')).toBeInTheDocument()
  })
})
