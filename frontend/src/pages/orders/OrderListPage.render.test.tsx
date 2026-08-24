import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import OrderListPage from './OrderListPage'
import { useAuthStore } from '../../store/authStore'

// 渲染冒烟 + 弹窗表单 2 列布局回归（FR-S19 列表页视觉统一）
vi.mock('../../services/orderService', () => ({
  fetchOrders: vi.fn(async () => ({ items: [], total: 0, page: 1, pageSize: 20 })),
  createOrder: vi.fn(),
  deleteOrder: vi.fn(),
}))
vi.mock('../../services/customerService', () => ({
  fetchCustomers: vi.fn(async () => ({ items: [], total: 0, page: 1, pageSize: 20 })),
}))
vi.mock('../../services/contractService', () => ({
  fetchContracts: vi.fn(async () => ({ items: [], total: 0, page: 1, pageSize: 20 })),
}))

const adminUser = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' as const }

describe('OrderListPage 渲染冒烟 + 弹窗表单 2 列（FR-S19）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    useAuthStore.setState({ user: adminUser })
  })

  it('首次渲染不抛异常并显示标题', async () => {
    expect(() => renderWithProviders(<OrderListPage />, { route: '/orders' })).not.toThrow()
    expect(await screen.findByText('订单管理', {}, { timeout: 5000 })).toBeInTheDocument()
  })
})
