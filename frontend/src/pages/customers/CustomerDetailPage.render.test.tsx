import { describe, expect, it, vi, beforeEach } from 'vitest'
import { Route, Routes } from 'react-router-dom'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import CustomerDetailPage from './CustomerDetailPage'
import { useAuthStore } from '../../store/authStore'

vi.mock('../../services/customerService', () => ({
  fetchCustomer: vi.fn(),
  fetchCustomers: vi.fn(),
  fetchAtRiskCustomers: vi.fn(),
}))
vi.mock('../../services/followUpService', () => ({
  fetchFollowUps: vi.fn(async () => ({ items: [], total: 0, page: 1, pageSize: 50 })),
}))
vi.mock('../../services/contactService', () => ({
  fetchContacts: vi.fn(async () => ({ items: [], total: 0, page: 1, pageSize: 50 })),
}))
vi.mock('../../services/customerShareService', () => ({
  shareCustomer: vi.fn(),
}))

const adminUser = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' as const }

const detail = {
  id: 1,
  name: 'Acme 科技',
  company: 'Acme Inc.',
  status: 'ACTIVE',
  version: 0,
  opportunities: [],
  followUps: [],
  contacts: [],
  customer360: {
    orders: [{ id: 10, orderNo: 'SO-001', title: '采购订单', amount: 1500000, status: 'PAID' }],
    paymentSummaries: [
      { orderId: 10, orderNo: 'SO-001', totalPlan: 1500000, paid: 1500000, overdue: 0 },
    ],
    contracts: [{ id: 5, contractNo: 'HT-001', title: '服务合同', amount: 1500000, status: 'EFFECTIVE' }],
    tickets: [{ id: 8, title: '登录问题', priority: 'HIGH', status: 'OPEN', slaStatus: 'NORMAL' }],
    amountSummary: { totalOrder: 1500000, paid: 1500000, dueOverdue: 0 },
    health: { score: 82, level: 'GREEN', deductions: [{ dimension: '跟进活跃度', deduct: 8 }] },
  },
}

describe('CustomerDetailPage（018 客户 360 渲染冒烟）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    useAuthStore.setState({ user: adminUser })
  })

  it('渲染客户 360：健康度评分 + 订单/合同/工单 Tabs 不崩溃', async () => {
    const { fetchCustomer } = await import('../../services/customerService')
    vi.mocked(fetchCustomer).mockResolvedValue(detail as never)

    renderWithProviders(
      <Routes>
        <Route path="/customers/:id" element={<CustomerDetailPage />} />
      </Routes>,
      { route: '/customers/1' },
    )

    expect((await screen.findAllByText('Acme 科技', {}, { timeout: 5000 })).length).toBeGreaterThan(0)
    // 客户 360 Tabs
    expect(await screen.findByText('客户 360', {}, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.getByText('健康度评分')).toBeInTheDocument()
    expect(screen.getByText('82')).toBeInTheDocument()
  })

  it('客户无 customer360 数据时不崩溃', async () => {
    const { fetchCustomer } = await import('../../services/customerService')
    vi.mocked(fetchCustomer).mockResolvedValue({
      id: 2,
      name: '无数据客户',
      company: 'Empty Co.',
      status: 'ACTIVE',
      version: 0,
      opportunities: [],
      followUps: [],
      contacts: [],
    } as never)

    renderWithProviders(
      <Routes>
        <Route path="/customers/:id" element={<CustomerDetailPage />} />
      </Routes>,
      { route: '/customers/2' },
    )

    expect((await screen.findAllByText('无数据客户', {}, { timeout: 5000 })).length).toBeGreaterThan(0)
  })
})
