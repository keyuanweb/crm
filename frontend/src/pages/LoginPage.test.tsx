import { describe, expect, it, vi, beforeEach } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { renderWithProviders } from '../test/renderWithProviders'
import LoginPage from './LoginPage'
import { fetchCaptcha, login } from '../services/authService'

vi.mock('../services/authService', () => ({
  login: vi.fn(),
  fetchCaptcha: vi.fn(),
}))

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    vi.mocked(fetchCaptcha).mockResolvedValue({
      captchaId: 'captcha-1',
      imageBase64: 'data:image/png;base64,AAAA',
    })
  })

  it('渲染登录表单、验证码图片与输入框，使用输入的用户名密码与验证码提交', async () => {
    vi.mocked(login).mockResolvedValue({
      accessToken: 'access-1',
      refreshToken: 'refresh-1',
      user: { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' },
    })

    renderWithProviders(<LoginPage />)

    expect(screen.getByLabelText('login.username')).toBeInTheDocument()
    expect(screen.getByLabelText('login.password')).toBeInTheDocument()
    expect(await screen.findByLabelText('login.captcha')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('login.username'), { target: { value: 'admin' } })
    fireEvent.change(screen.getByLabelText('login.password'), { target: { value: 'admin123' } })
    fireEvent.change(screen.getByLabelText('login.captcha'), { target: { value: '7gk2' } })
    fireEvent.click(screen.getByRole('button', { name: /login\.submit/ }))

    await waitFor(() => {
      expect(login).toHaveBeenCalledWith('admin', 'admin123', 'captcha-1', '7gk2')
    })
  })

  it('点击验证码图片触发刷新', async () => {
    renderWithProviders(<LoginPage />)

    // 验证码图片的 alt/aria-label 是硬编码中文（非 i18n）
    const img = await screen.findByRole('img', { name: /验证码图片/ })
    fireEvent.click(img)

    await waitFor(() => {
      expect(fetchCaptcha).toHaveBeenCalledTimes(2) // 初始一次 + 点击一次
    })
  })

  it('登录失败时展示错误信息并刷新验证码', async () => {
    // 模拟一个真正的 Axios 错误对象
    const axiosError = new Error('用户名或密码错误') as any
    axiosError.isAxiosError = true
    axiosError.response = { data: { error: { message: '用户名或密码错误' } } }
    vi.mocked(login).mockRejectedValue(axiosError)

    renderWithProviders(<LoginPage />)

    fireEvent.change(screen.getByLabelText('login.username'), { target: { value: 'admin' } })
    fireEvent.change(screen.getByLabelText('login.password'), { target: { value: 'wrong' } })
    fireEvent.change(screen.getByLabelText('login.captcha'), { target: { value: '7gk2' } })
    fireEvent.click(screen.getByRole('button', { name: /login\.submit/ }))

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('用户名或密码错误')
    })
    // 失败后刷新验证码（初始 + 失败刷新）
    await waitFor(() => {
      expect(vi.mocked(fetchCaptcha).mock.calls.length).toBeGreaterThanOrEqual(2)
    })
  })
})
