import { describe, expect, it, vi, beforeEach } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { renderWithProviders } from '../test/renderWithProviders'
import LoginPage from './LoginPage'
import { fetchCaptcha, login } from '../services/authService'

/**
 * 只桩掉有 I/O 的两个函数，**保留模块里真实的类型守卫**（`hasTokens` / `isMfaChallenge`）。
 *
 * <p>本仓其他测试文件的 `vi.mock` 都是"把整个模块换成 `{ f: vi.fn() }`"的写法，这里刻意不同：
 * `LoginPage` 的两个成功分支正是由这两个守卫分流的。若把它们也换成 `vi.fn()`，测试就得自己
 * 编排"什么时候算 MFA 挑战"，于是**验证的是测试里的那个判据，而不是线上跑的那个** ——
 * 守卫一旦被改坏（比如 `mfaRequired === true` 写成只判 `typeof mfaToken === 'string'`），
 * 全绿的用例会替它担保。守卫是纯函数、不碰 I/O，用真的即可。
 */
vi.mock('../services/authService', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../services/authService')>()
  return { ...actual, login: vi.fn(), fetchCaptcha: vi.fn() }
})

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

    // 098：验证码图片的 alt/aria-label 已改走 i18n；此处断言的是**键名**
    // （`src/test/setup.ts` 的 i18n mock 让 `t(key)` 原样返回 key，缺键会直接抛错）。
    const img = await screen.findByRole('img', { name: /login\.captchaImage/ })
    fireEvent.click(img)

    await waitFor(() => {
      expect(fetchCaptcha).toHaveBeenCalledTimes(2) // 初始一次 + 点击一次
    })
  })

  it('登录失败时展示错误信息并刷新验证码', async () => {
    // 模拟一个真正的 Axios 错误对象
    const axiosError = Object.assign(new Error('用户名或密码错误'), {
      isAxiosError: true,
      response: { data: { error: { message: '用户名或密码错误' } } },
    })
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
