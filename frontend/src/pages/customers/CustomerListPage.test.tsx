import { describe, expect, it, vi, beforeEach } from 'vitest'
import { screen, waitFor, fireEvent, within } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import CustomerListPage from './CustomerListPage'
import { useAuthStore } from '../../store/authStore'

vi.mock('../../services/customerService', () => ({
  fetchCustomers: vi.fn(),
  createCustomer: vi.fn(),
  updateCustomer: vi.fn(),
  deleteCustomer: vi.fn(),
  fetchMyCustomers: vi.fn(),
  fetchPoolCustomers: vi.fn(),
  batchTransferCustomers: vi.fn(),
  claimCustomer: vi.fn(),
  exportCustomers: vi.fn(),
  importCustomers: vi.fn(),
  downloadTemplate: vi.fn(),
  scanPool: vi.fn(),
}))
vi.mock('../../services/userService', () => ({
  fetchUsers: vi.fn(async () => ({ items: [], total: 0, page: 1, pageSize: 20 })),
}))
vi.mock('../../services/marketingService', () => ({
  fetchCampaigns: vi.fn(async () => ({ items: [], total: 0, page: 1, pageSize: 100 })),
}))
vi.mock('../../hooks/useCustomFieldFilters', () => ({
  useCustomFieldFilterColumns: () => [],
  extractCfParams: () => ({}),
}))
vi.mock('../../components/CustomFieldItems', () => ({
  CustomFieldFormItems: () => null,
}))
vi.mock('../../utils/customField', () => ({
  fromCustomFieldValues: () => ({}),
  toCustomFieldPayload: () => ({}),
}))

const adminUser = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' as const }

const pageResult = (items: unknown[], total: number) => ({ items, total, page: 1, pageSize: 20 })

describe('CustomerListPage（交互测试：列表 + 新增客户）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    useAuthStore.setState({ user: adminUser })
  })

  it('渲染客户列表（ProTable request 加载数据）', async () => {
    const { fetchCustomers, fetchMyCustomers, fetchPoolCustomers } = await import('../../services/customerService')
    vi.mocked(fetchCustomers).mockResolvedValue(
      pageResult([{ id: 1, name: 'Acme 科技', company: 'Acme Inc.', status: 'ACTIVE', version: 0 }], 1) as never,
    )
    vi.mocked(fetchMyCustomers).mockResolvedValue(pageResult([], 0) as never)
    vi.mocked(fetchPoolCustomers).mockResolvedValue(pageResult([], 0) as never)

    renderWithProviders(<CustomerListPage />)

    expect(await screen.findByText('Acme 科技')).toBeInTheDocument()
  })

  it('点击"新增客户"打开弹窗，填写并提交调用 createCustomer', async () => {
    const { fetchCustomers, fetchMyCustomers, fetchPoolCustomers, createCustomer } = await import('../../services/customerService')
    vi.mocked(fetchCustomers).mockResolvedValue(pageResult([], 0) as never)
    vi.mocked(fetchMyCustomers).mockResolvedValue(pageResult([], 0) as never)
    vi.mocked(fetchPoolCustomers).mockResolvedValue(pageResult([], 0) as never)
    vi.mocked(createCustomer).mockResolvedValue({ id: 9, name: '新客户', version: 0 } as never)

    renderWithProviders(<CustomerListPage />)

    // 打开新增弹窗
    fireEvent.click(screen.getByRole('button', { name: /新增客户/ }))
    // 弹窗出现（标题含"新增客户"，用 dialog 定位）
    const dialog = await screen.findByRole('dialog')
    expect(dialog).toBeInTheDocument()

    // 在弹窗范围内填写必填字段（避免与搜索表单的"客户名称"标签冲突）
    fireEvent.change(within(dialog).getByLabelText('客户名称'), { target: { value: '新客户' } })
    fireEvent.change(within(dialog).getByLabelText('公司'), { target: { value: '新公司' } })

    // 提交
    fireEvent.click(within(dialog).getByRole('button', { name: /保\s*存/ }))

    await waitFor(() => {
      expect(createCustomer).toHaveBeenCalledWith(
        expect.objectContaining({ name: '新客户', company: '新公司' }),
      )
    })
  })
})
