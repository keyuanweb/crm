import { describe, expect, it, vi, beforeEach } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { renderWithProviders } from '../test/renderWithProviders'
import LoginPage from './LoginPage'
import { fetchCaptcha, login, verifyMfa } from '../services/authService'
import { useAuthStore } from '../store/authStore'

/**
 * 登录二次验证（082，US1/US2）。
 *
 * <p>这个文件守的是本批**唯一一处前后端不可独立发布**的地方：密码阶段的响应现在有两种形状，
 * 而 `LoginPage` 原先无条件读 `res.accessToken`。若那条分支写错，故障不是报错，而是
 * **静默重定向环** —— `setTokens(undefined, undefined)` 把字符串 `"undefined"` 写进
 * localStorage ⇒ `isAuthenticated()` 恒真 ⇒ 外壳渲染 ⇒ `fetchMe()` 401 ⇒ 401 拦截器硬跳
 * `/login` ⇒ 回到登录页再次提交……。所以第 1 条用例的第一断言不是"出现了码输入框"，
 * 而是 **"没有写下任何令牌"**。
 *
 * <p>与 `LoginPage.test.tsx` 同样只桩 I/O、保留真实的类型守卫（理由见那个文件的 `vi.mock` 注释）。
 */
vi.mock('../services/authService', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../services/authService')>()
  return { ...actual, login: vi.fn(), fetchCaptcha: vi.fn(), verifyMfa: vi.fn() }
})

/** 认证器 App 里当前显示的码。 */
const VALID_CODE = '123456'

const enrolledUser = { id: 1, username: 'admin', displayName: '系统管理员', role: 'ADMIN' }

/** 后端 401 的形状（`ApiResponse.fail` → `{ error: { code, message } }`）。 */
const apiError = (status: number, code: string, message: string) =>
  Object.assign(new Error(message), {
    isAxiosError: true,
    response: { status, data: { error: { code, message } } },
  })

/** 填完密码视图并提交。 */
const submitPassword = async (password = 'admin123') => {
  fireEvent.change(screen.getByLabelText('login.username'), { target: { value: 'admin' } })
  fireEvent.change(screen.getByLabelText('login.password'), { target: { value: password } })
  fireEvent.change(await screen.findByLabelText('login.captcha'), { target: { value: '7gk2' } })
  fireEvent.click(screen.getByRole('button', { name: /login\.submit/ }))
}

/** 提交二次验证。 */
const submitMfa = (label: string, value: string) => {
  fireEvent.change(screen.getByLabelText(label), { target: { value } })
  fireEvent.click(screen.getByRole('button', { name: /login\.mfaSubmit/ }))
}

describe('LoginPage 二次验证', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    // zustand store 是模块级单例，localStorage.clear() 清不掉它
    useAuthStore.setState({ user: null })
    vi.mocked(fetchCaptcha).mockResolvedValue({
      captchaId: 'captcha-1',
      imageBase64: 'data:image/png;base64,AAAA',
    })
  })

  it('密码正确但账号启用了 2FA 时切到二次验证视图，且不写入任何令牌', async () => {
    vi.mocked(login).mockResolvedValue({
      mfaRequired: true,
      mfaToken: 'ticket-1',
      expiresIn: 300,
    })

    renderWithProviders(<LoginPage />)
    await submitPassword()

    // 1) 换视图：码输入框在，密码表单不在
    expect(await screen.findByLabelText('login.mfaCode')).toBeInTheDocument()
    expect(screen.queryByLabelText('login.password')).not.toBeInTheDocument()
    expect(screen.getByText('login.mfaTitle')).toBeInTheDocument()

    // 2) 这一条才是这个文件存在的理由：磁盘上不能出现 "undefined" 令牌，
    //    否则外壳会以为已登录，进而与 401 拦截器组成静默重定向环。
    expect(localStorage.getItem('accessToken')).toBeNull()
    expect(localStorage.getItem('refreshToken')).toBeNull()
  })

  it('提交正确的动态码后写入令牌、写入用户', async () => {
    vi.mocked(login).mockResolvedValue({
      mfaRequired: true,
      mfaToken: 'ticket-1',
      expiresIn: 300,
    })
    vi.mocked(verifyMfa).mockResolvedValue({
      accessToken: 'access-2',
      refreshToken: 'refresh-2',
      user: enrolledUser,
    })

    renderWithProviders(<LoginPage />)
    await submitPassword()
    await screen.findByLabelText('login.mfaCode')
    submitMfa('login.mfaCode', VALID_CODE)

    await waitFor(() => {
      expect(localStorage.getItem('accessToken')).toBe('access-2')
    })
    expect(localStorage.getItem('refreshToken')).toBe('refresh-2')
    expect(useAuthStore.getState().user?.username).toBe('admin')
    // 票据原样回传（服务端靠它找回 userId）；码在提交前被 trim 过
    expect(vi.mocked(verifyMfa)).toHaveBeenCalledWith('ticket-1', { code: VALID_CODE })
  })

  it('切到「使用恢复码」后提交的是恢复码字段，而不是动态码', async () => {
    vi.mocked(login).mockResolvedValue({
      mfaRequired: true,
      mfaToken: 'ticket-1',
      expiresIn: 300,
    })
    vi.mocked(verifyMfa).mockResolvedValue({
      accessToken: 'access-3',
      refreshToken: 'refresh-3',
      user: enrolledUser,
    })

    renderWithProviders(<LoginPage />)
    await submitPassword()
    await screen.findByLabelText('login.mfaCode')

    fireEvent.click(screen.getByRole('button', { name: /login\.mfaUseRecovery/ }))
    // 两个输入框互斥：切换后动态码那一个必须消失（否则用户会对着两个框不知道该填哪个）
    expect(screen.queryByLabelText('login.mfaCode')).not.toBeInTheDocument()

    submitMfa('login.mfaRecoveryCode', 'ABCD2345')

    await waitFor(() => {
      expect(vi.mocked(verifyMfa)).toHaveBeenCalledWith('ticket-1', {
        recoveryCode: 'ABCD2345',
      })
    })
    // 恢复码那一支**不带 code 字段**：后端按"传了什么"选分支，两个都传会让实际被验的
    // 凭据取决于服务端的优先级，而不是用户的意图。
    expect(vi.mocked(verifyMfa).mock.calls[0][1]).not.toHaveProperty('code')
  })

  it('动态码错误时留在二次验证视图并给出后端文案，票据不被丢弃', async () => {
    vi.mocked(login).mockResolvedValue({
      mfaRequired: true,
      mfaToken: 'ticket-1',
      expiresIn: 300,
    })
    vi.mocked(verifyMfa).mockRejectedValue(
      apiError(401, 'MFA_CODE_INVALID', '动态码错误，剩余 4 次机会'),
    )

    renderWithProviders(<LoginPage />)
    await submitPassword()
    await screen.findByLabelText('login.mfaCode')
    submitMfa('login.mfaCode', '000000')

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('动态码错误，剩余 4 次机会')
    })
    // 还在二次验证视图：码错了是可以立刻重试的，票据仍然有效
    expect(screen.getByLabelText('login.mfaCode')).toBeInTheDocument()
    expect(localStorage.getItem('accessToken')).toBeNull()

    // 重试时要复用同一张票据（换票据等于放弃已经数过的失败次数）
    submitMfa('login.mfaCode', VALID_CODE)
    await waitFor(() => {
      expect(vi.mocked(verifyMfa)).toHaveBeenCalledTimes(2)
    })
    expect(vi.mocked(verifyMfa).mock.calls[1][0]).toBe('ticket-1')
  })

  it('票据失效时退回密码视图，而不是让用户对着死票据反复试码', async () => {
    vi.mocked(login).mockResolvedValue({
      mfaRequired: true,
      mfaToken: 'ticket-1',
      expiresIn: 300,
    })
    vi.mocked(verifyMfa).mockRejectedValue(
      apiError(401, 'MFA_TICKET_INVALID', '二次验证票据无效或已过期，请重新登录'),
    )

    renderWithProviders(<LoginPage />)
    await submitPassword()
    await screen.findByLabelText('login.mfaCode')
    submitMfa('login.mfaCode', VALID_CODE)

    await waitFor(() => {
      expect(screen.getByLabelText('login.username')).toBeInTheDocument()
    })
    // 留在原视图是**永远不可能成功**的：票据是一次性的，它已经没了。
    // 所以这里的判据不是"显示了错误"，而是"回到了密码阶段"。
    expect(screen.queryByLabelText('login.mfaCode')).not.toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('login.mfaExpired')
    // 退回去之后验证码要换一张（旧的那张可能与密码表单的 captchaId 不同步）
    expect(vi.mocked(fetchCaptcha).mock.calls.length).toBeGreaterThanOrEqual(2)
  })

  it('「返回上一步」清掉票据，重新登录会拿到新票据', async () => {
    vi.mocked(login)
      .mockResolvedValueOnce({ mfaRequired: true, mfaToken: 'ticket-1', expiresIn: 300 })
      .mockResolvedValueOnce({ mfaRequired: true, mfaToken: 'ticket-2', expiresIn: 300 })
    vi.mocked(verifyMfa).mockResolvedValue({
      accessToken: 'access-4',
      refreshToken: 'refresh-4',
      user: enrolledUser,
    })

    renderWithProviders(<LoginPage />)
    await submitPassword()
    await screen.findByLabelText('login.mfaCode')

    fireEvent.click(screen.getByRole('button', { name: /login\.mfaBack/ }))
    expect(screen.getByLabelText('login.password')).toBeInTheDocument()

    await submitPassword()
    await screen.findByLabelText('login.mfaCode')
    submitMfa('login.mfaCode', VALID_CODE)

    await waitFor(() => {
      expect(vi.mocked(verifyMfa)).toHaveBeenCalledWith('ticket-2', { code: VALID_CODE })
    })
  })

  it('动态码不足 6 位时不发请求（服务端会把畸形码计入失败次数）', async () => {
    vi.mocked(login).mockResolvedValue({
      mfaRequired: true,
      mfaToken: 'ticket-1',
      expiresIn: 300,
    })

    renderWithProviders(<LoginPage />)
    await submitPassword()
    await screen.findByLabelText('login.mfaCode')

    submitMfa('login.mfaCode', '12345')

    await waitFor(() => {
      expect(screen.getByText('login.mfaCodeFormat')).toBeInTheDocument()
    })
    // 后端的 matchTimeStep 对形态不合法的码直接返回"没匹配上"，那会走**记一次失败**的分支
    // ——输错长度就白烧掉 5 次机会里的一次。前端这道规则因此不是装饰性的。
    expect(vi.mocked(verifyMfa)).not.toHaveBeenCalled()
  })

  it('响应既无令牌也无票据时如实报错，不写令牌', async () => {
    vi.mocked(login).mockResolvedValue({})

    renderWithProviders(<LoginPage />)
    await submitPassword()

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('login.mfaUnexpected')
    })
    expect(localStorage.getItem('accessToken')).toBeNull()
    // 契约之外的形状不猜、不兜底，但用户必须能重试
    expect(screen.getByLabelText('login.password')).toBeInTheDocument()
  })

  it('只带 mfaRequired、没有票据时按契约外形状处理，不切到提交不动的验证视图', async () => {
    // 本条是 §I1 的破坏暴露出来的缺口：上面那条只钉住了「两种都没有」，
    // 而 `isMfaChallenge` 还有另一半——**票据必须非空**。少了它，这一支会切到
    // 二次验证视图，而那里的提交入口第一行就是 `if (!mfaToken) return`：
    // 用户会停在一个**按提交毫无反应**的页面上，既没有报错也不知道该回上一步。
    vi.mocked(login).mockResolvedValue({ mfaRequired: true })

    renderWithProviders(<LoginPage />)
    await submitPassword()

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('login.mfaUnexpected')
    })
    expect(screen.queryByLabelText('login.mfaCode')).not.toBeInTheDocument()
    expect(screen.getByLabelText('login.password')).toBeInTheDocument()
    expect(localStorage.getItem('accessToken')).toBeNull()
  })
})
