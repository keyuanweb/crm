import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import UserManagementPage from './UserManagementPage'
import { createUser, fetchUsers } from '../../services/userService'
import { useAuthStore, type UserInfo } from '../../store/authStore'
import type { User } from '../../types/user'

vi.mock('../../services/userService', () => ({
  fetchUsers: vi.fn(),
  createUser: vi.fn(),
  updateUser: vi.fn(),
  resetPassword: vi.fn(),
}))

const adminUser: UserInfo = { id: 1, username: 'admin', displayName: '管理员', role: 'ADMIN' }

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <UserManagementPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('UserManagementPage（T019）', () => {
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
    renderPage()
    expect(screen.getByText('用户管理')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText('暂无用户')).toBeInTheDocument())
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
    renderPage()

    fireEvent.click(await screen.findByRole('button', { name: '新增用户' }))
    fireEvent.change(await screen.findByLabelText(/用户名/), { target: { value: 'sales01' } })
    fireEvent.change(screen.getByLabelText(/显示名/), { target: { value: '销售一' } })
    fireEvent.change(screen.getByLabelText(/初始密码/), { target: { value: 'pass1234' } })
    fireEvent.click(screen.getByRole('button', { name: '创建' }))

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

  it('点击停用当前登录账号提示错误且不发起请求（FR-007）', async () => {
    vi.mocked(fetchUsers).mockResolvedValue({
      items: [
        { id: 1, username: 'admin', displayName: '管理员', role: 'ADMIN', enabled: true, version: 0 },
      ],
      total: 1,
      page: 1,
      pageSize: 20,
    })
    renderPage()

    const disableBtn = await screen.findByRole('button', { name: '停用' })
    fireEvent.click(disableBtn)

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('不能停用当前登录账号'),
    )
  })
})
