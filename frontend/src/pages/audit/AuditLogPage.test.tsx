import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import AuditLogPage from './AuditLogPage'
import { fetchAuditLogs } from '../../services/auditLogService'
import type { AuditLog } from '../../types/auditLog'

vi.mock('../../services/auditLogService', () => ({
  fetchAuditLogs: vi.fn(),
}))

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AuditLogPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('AuditLogPage（T071）', () => {
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
    renderPage()
    expect(screen.getByText('审计日志')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText('暂无审计记录')).toBeInTheDocument())
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
    renderPage()
    const table = await screen.findByRole('table')
    await waitFor(() => expect(within(table).getByText('admin')).toBeInTheDocument())
    expect(within(table).getByText('创建')).toBeInTheDocument()
    expect(within(table).getByText('客户')).toBeInTheDocument()
    expect(within(table).getByText('42')).toBeInTheDocument()
  })
})
