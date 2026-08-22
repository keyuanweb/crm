import { describe, expect, it, vi, beforeEach } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { renderWithProviders } from '../test/renderWithProviders'
import LoginPage from './LoginPage'
import { login } from '../services/authService'

vi.mock('../services/authService', () => ({
  login: vi.fn(),
}))

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('渲染登录表单并使用输入的用户名密码提交', async () => {
    vi.mocked(login).mockResolvedValue({
      accessToken: 'access-1',
      refreshToken: 'refresh-1',
      user: { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' },
    })

    renderWithProviders(<LoginPage />)

    expect(screen.getByLabelText('用户名')).toBeInTheDocument()
    expect(screen.getByLabelText('密码')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('用户名'), { target: { value: 'admin' } })
    fireEvent.change(screen.getByLabelText('密码'), { target: { value: 'admin123' } })
    fireEvent.click(screen.getByRole('button', { name: /登\s*录/ }))

    await waitFor(() => {
      expect(login).toHaveBeenCalledWith('admin', 'admin123')
    })
  })

  it('登录失败时展示错误信息', async () => {
    vi.mocked(login).mockRejectedValue({
      isAxiosError: true,
      response: { data: { error: { message: '用户名或密码错误' } } },
    })

    renderWithProviders(<LoginPage />)

    fireEvent.change(screen.getByLabelText('用户名'), { target: { value: 'admin' } })
    fireEvent.change(screen.getByLabelText('密码'), { target: { value: 'wrong' } })
    fireEvent.click(screen.getByRole('button', { name: /登\s*录/ }))

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('用户名或密码错误')
    })
  })
})
