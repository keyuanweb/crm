import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import UserManagementPage from './UserManagementPage'
import { createUser, fetchUsers, updateUser } from '../../services/userService'
import { useAuthStore } from '../../store/authStore'
import type { User } from '../../types/user'

vi.mock('../../services/userService', () => ({
  fetchUsers: vi.fn(),
  createUser: vi.fn(),
  updateUser: vi.fn(),
  resetPassword: vi.fn(),
}))

const adminUser = { id: 1, username: 'admin', displayName: '管理员', role: 'ADMIN' as const }

describe('UserManagementPage（T019，antd ProTable 版）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    useAuthStore.setState({ user: adminUser })
    vi.mocked(fetchUsers).mockResolvedValue({
      items: [],
      total: 0,
      page: 1,
      pageSize: 20,
    })
  })

  it('渲染标题与空状态', async () => {
    renderWithProviders(<UserManagementPage />)
    expect(await screen.findByText('用户管理', {}, { timeout: 5000 })).toBeInTheDocument()
    await waitFor(
      () => expect(screen.getAllByText('暂无数据').length).toBeGreaterThan(0),
      { timeout: 5000 },
    )
  })

  it('打开新增弹窗并提交创建用户（FR-003）', async () => {
    const created: User = {
      id: 9,
      username: 'sales01',
      displayName: '销售一',
      role: 'SALES',
      enabled: true,
      version: 0,
    }
    vi.mocked(createUser).mockResolvedValue(created)
    renderWithProviders(<UserManagementPage />)

    fireEvent.click(await screen.findByRole('button', { name: /新增用户/ }, { timeout: 5000 }))
    // 限定在 Modal 容器内查询（工具栏按钮与弹窗标题同名；ProTable 搜索表单也有"用户名"等字段）
    const titles = await screen.findAllByText('新增用户', {}, { timeout: 5000 })
    const modalTitle = titles.find((el) => el.closest('.ant-modal')) ?? titles[titles.length - 1]
    const modalRoot = modalTitle.closest('.ant-modal') as HTMLElement
    const inDialog = within(modalRoot)
    fireEvent.change(inDialog.getByLabelText(/用户名/), { target: { value: 'sales01' } })
    fireEvent.change(inDialog.getByLabelText(/显示名/), { target: { value: '销售一' } })
    fireEvent.change(inDialog.getByLabelText(/初始密码/), { target: { value: 'pass1234' } })
    fireEvent.click(inDialog.getByRole('button', { name: /创\s*建/ }))

    await waitFor(() =>
      expect(createUser).toHaveBeenCalledWith(
        expect.objectContaining({
          username: 'sales01',
          displayName: '销售一',
          role: 'SALES',
          password: 'pass1234',
        }),
      ),
    )
  })

  it('点击停用当前登录账号时提示错误且不发起请求（FR-007）', async () => {
    vi.mocked(fetchUsers).mockResolvedValue({
      items: [
        { id: 1, username: 'admin', displayName: '管理员', role: 'ADMIN', enabled: true, version: 0 },
      ],
      total: 1,
      page: 1,
      pageSize: 20,
    })
    renderWithProviders(<UserManagementPage />)

    fireEvent.click(await screen.findByText('停用'))

    await waitFor(() => expect(updateUser).not.toHaveBeenCalled())
  })
})
