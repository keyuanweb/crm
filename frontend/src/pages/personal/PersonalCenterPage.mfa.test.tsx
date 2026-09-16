import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { renderWithProviders } from '../../test/renderWithProviders'
import PersonalCenterPage from './PersonalCenterPage'
import * as personalService from '../../services/personalService'
import * as mfaService from '../../services/mfaService'

/**
 * 账户安全卡里的双因素认证区块（082，US2–US4）。
 *
 * <p>这个文件守的主要不是"按钮点了会发请求"，而是**两个安全语义**：
 * ① "状态未知"不能被画成"未启用"——前者是没问到，后者是一个确定的安全状态，
 * 混为一谈会让用户以为自己的第二因素被关掉了；
 * ② 恢复码只在生成它的那一次响应里存在，所以界面必须**逼着用户先确认抄下来了**，
 * 才允许关掉那个弹窗——关掉之后就再也取不回来了。
 */
vi.mock('../../services/personalService')
vi.mock('../../services/userService')
vi.mock('../../services/mfaService')

const personalInfo = {
  id: 1,
  username: 'admin',
  displayName: '管理员',
  role: 'ADMIN',
}

const disabledStatus = { enabled: false, recoveryCodesRemaining: 0 }
const enabledStatus = {
  enabled: true,
  enabledAt: '2026-09-16T10:00:00',
  recoveryCodesRemaining: 7,
}

/** 后端 400/401 的形状（`ApiResponse.fail` → `{ error: { code, message } }`）。 */
const apiError = (status: number, code: string, message: string) =>
  Object.assign(new Error(message), {
    isAxiosError: true,
    response: { status, data: { error: { code, message } } },
  })

const renderPage = async () => {
  vi.mocked(personalService.fetchPersonalInfo).mockResolvedValue(personalInfo as never)
  renderWithProviders(<PersonalCenterPage />)
  await waitFor(() => {
    expect(screen.getByTestId('security-card')).toBeInTheDocument()
  })
}

/** 安全卡上那一个操作按钮（三个入口互斥，故按名字取即可）。 */
const actionButton = (key: string) => screen.getByRole('button', { name: new RegExp(key) })

describe('PersonalCenterPage 双因素认证', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('未启用时给出启用入口，且不显示关闭 / 重新生成', async () => {
    vi.mocked(mfaService.fetchMfaStatus).mockResolvedValue(disabledStatus)
    await renderPage()

    await waitFor(() => {
      expect(screen.getByText('pages.personalCenter.mfaDisabled')).toBeInTheDocument()
    })
    expect(actionButton('pages.personalCenter.btnEnableMfa')).toBeInTheDocument()
    // 未启用时这两个动作点了都是 400，"摆出来但点了报错"会让用户以为是自己点错了
    expect(screen.queryByText('pages.personalCenter.btnDisableMfa')).not.toBeInTheDocument()
    expect(screen.queryByText('pages.personalCenter.btnRegenerateRecovery')).not.toBeInTheDocument()
  })

  it('已启用时展示绑定时间与剩余恢复码，并只给关闭 / 重新生成', async () => {
    vi.mocked(mfaService.fetchMfaStatus).mockResolvedValue(enabledStatus)
    await renderPage()

    await waitFor(() => {
      expect(screen.getByText('pages.personalCenter.mfaEnabled')).toBeInTheDocument()
    })
    const card = screen.getByTestId('security-card')
    expect(card.textContent).toContain('pages.personalCenter.mfaEnabledAt')
    expect(card.textContent).toContain('pages.personalCenter.mfaRecoveryRemaining')
    expect(actionButton('pages.personalCenter.btnDisableMfa')).toBeInTheDocument()
    expect(actionButton('pages.personalCenter.btnRegenerateRecovery')).toBeInTheDocument()
    expect(screen.queryByText('pages.personalCenter.btnEnableMfa')).not.toBeInTheDocument()
  })

  it('状态查询失败时显示"状态未知"并可重试，不谎报为"未启用"', async () => {
    vi.mocked(mfaService.fetchMfaStatus).mockRejectedValueOnce(
      apiError(503, 'MFA_STORE_UNAVAILABLE', '认证状态存储不可用，请稍后重试'),
    )
    vi.mocked(mfaService.fetchMfaStatus).mockResolvedValueOnce(enabledStatus)
    await renderPage()

    await waitFor(() => {
      expect(screen.getByText('pages.personalCenter.mfaUnknown')).toBeInTheDocument()
    })
    // 关键：**任何**操作按钮都不给。"没问到"与"未启用"混同的代价是用户以为第二因素被关了；
    // 而此刻如果给出"启用"入口，一个其实已启用的账号点下去会拿到 400。
    expect(screen.queryByText('pages.personalCenter.btnEnableMfa')).not.toBeInTheDocument()
    expect(screen.queryByText('pages.personalCenter.mfaDisabled')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /pages\.personalCenter\.btnRetry/ }))
    await waitFor(() => {
      expect(screen.getByText('pages.personalCenter.mfaEnabled')).toBeInTheDocument()
    })
  })

  it('启用向导：扫码 → 输码 → 展示恢复码；未确认抄下前不能关掉弹窗', async () => {
    vi.mocked(mfaService.fetchMfaStatus).mockResolvedValue(disabledStatus)
    vi.mocked(mfaService.setupMfa).mockResolvedValue({
      secret: 'JBSWY3DPEHPK3PXP',
      otpauthUrl: 'otpauth://totp/CRM:admin?secret=JBSWY3DPEHPK3PXP',
      qrCodeDataUrl: 'data:image/png;base64,AAAA',
      enabled: false,
    })
    const codes = ['ABCD2345', 'EFGH2345', 'JKLM2345']
    vi.mocked(mfaService.enableMfa).mockResolvedValue({ enabled: true, recoveryCodes: codes })

    await renderPage()
    await waitFor(() => {
      expect(screen.getByText('pages.personalCenter.mfaDisabled')).toBeInTheDocument()
    })

    fireEvent.click(actionButton('pages.personalCenter.btnEnableMfa'))

    // 第一步：二维码（后端给的就是 data URL，前端原样喂给 <img src>）+ 手动密钥兜底
    const qr = await screen.findByRole('img', { name: 'pages.personalCenter.mfaQrAlt' })
    expect(qr).toHaveAttribute('src', 'data:image/png;base64,AAAA')
    expect(screen.getByText('JBSWY3DPEHPK3PXP')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('pages.personalCenter.mfaCodeLabel'), {
      target: { value: '123456' },
    })
    fireEvent.click(screen.getByRole('button', { name: /pages\.personalCenter\.mfaEnableOk/ }))

    await waitFor(() => {
      expect(mfaService.enableMfa).toHaveBeenCalledWith('123456')
    })

    // 第二步：十个恢复码明文，且"完成"在勾选确认之前是禁用的 ——
    // 关掉这个弹窗之后，服务端只剩加盐哈希，这些码**再也取不回来**。
    await waitFor(() => {
      expect(screen.getByText('pages.personalCenter.mfaRecoveryNotice')).toBeInTheDocument()
    })
    codes.forEach((code) => expect(screen.getByText(code)).toBeInTheDocument())
    const done = screen.getByRole('button', { name: /pages\.personalCenter\.mfaRecoveryDone/ })
    expect(done).toBeDisabled()

    fireEvent.click(screen.getByRole('checkbox'))
    await waitFor(() => {
      expect(done).toBeEnabled()
    })

    // 状态刷新：完成后卡片变成"已启用"
    vi.mocked(mfaService.fetchMfaStatus).mockResolvedValue(enabledStatus)
    fireEvent.click(done)
    await waitFor(() => {
      expect(screen.getByText('pages.personalCenter.mfaEnabled')).toBeInTheDocument()
    })
    expect(screen.queryByText('pages.personalCenter.mfaRecoveryNotice')).not.toBeInTheDocument()
  })

  it('动态码错误时弹窗留在原地，并把后端的剩余次数原样带给用户', async () => {
    vi.mocked(mfaService.fetchMfaStatus).mockResolvedValue(disabledStatus)
    vi.mocked(mfaService.setupMfa).mockResolvedValue({
      secret: 'JBSWY3DPEHPK3PXP',
      otpauthUrl: 'otpauth://totp/CRM:admin?secret=JBSWY3DPEHPK3PXP',
      qrCodeDataUrl: 'data:image/png;base64,AAAA',
      enabled: false,
    })
    vi.mocked(mfaService.enableMfa).mockRejectedValue(
      apiError(401, 'MFA_CODE_INVALID', '动态码错误，剩余 4 次机会'),
    )

    await renderPage()
    await waitFor(() => {
      expect(screen.getByText('pages.personalCenter.mfaDisabled')).toBeInTheDocument()
    })
    fireEvent.click(actionButton('pages.personalCenter.btnEnableMfa'))

    await screen.findByRole('img', { name: 'pages.personalCenter.mfaQrAlt' })
    fireEvent.change(screen.getByLabelText('pages.personalCenter.mfaCodeLabel'), {
      target: { value: '000000' },
    })
    fireEvent.click(screen.getByRole('button', { name: /pages\.personalCenter\.mfaEnableOk/ }))

    // 用户手上就是那个认证器：关掉弹窗重来一遍只会让他在失败计数上多走一步
    await waitFor(() => {
      expect(screen.getByText('动态码错误，剩余 4 次机会')).toBeInTheDocument()
    })
    expect(screen.getByLabelText('pages.personalCenter.mfaCodeLabel')).toBeInTheDocument()
  })

  it('关闭双因素认证要密码 + 第二因素两样，成功后就地刷新为未启用', async () => {
    vi.mocked(mfaService.fetchMfaStatus).mockResolvedValue(enabledStatus)
    vi.mocked(mfaService.disableMfa).mockResolvedValue(undefined)

    await renderPage()
    await waitFor(() => {
      expect(screen.getByText('pages.personalCenter.mfaEnabled')).toBeInTheDocument()
    })
    fireEvent.click(actionButton('pages.personalCenter.btnDisableMfa'))

    fireEvent.change(screen.getByLabelText('pages.personalCenter.mfaPasswordLabel'), {
      target: { value: 'admin123' },
    })
    fireEvent.change(screen.getByLabelText('pages.personalCenter.mfaCodeOrRecoveryLabel'), {
      target: { value: '654321' },
    })

    vi.mocked(mfaService.fetchMfaStatus).mockResolvedValue(disabledStatus)
    fireEvent.click(screen.getByRole('button', { name: /pages\.personalCenter\.mfaDisableOk/ }))

    await waitFor(() => {
      expect(mfaService.disableMfa).toHaveBeenCalledWith({ password: 'admin123', code: '654321' })
    })
    await waitFor(() => {
      expect(screen.getByText('pages.personalCenter.mfaDisabled')).toBeInTheDocument()
    })
  })

  it('重新生成恢复码要先过密码，拿到新码后旧码即刻作废的警告在前', async () => {
    vi.mocked(mfaService.fetchMfaStatus).mockResolvedValue(enabledStatus)
    vi.mocked(mfaService.regenerateRecoveryCodes).mockResolvedValue(['ZZZZ2345'])

    await renderPage()
    await waitFor(() => {
      expect(screen.getByText('pages.personalCenter.mfaEnabled')).toBeInTheDocument()
    })
    fireEvent.click(actionButton('pages.personalCenter.btnRegenerateRecovery'))

    expect(screen.getByText('pages.personalCenter.mfaRegenerateWarning')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('pages.personalCenter.mfaPasswordLabel'), {
      target: { value: 'admin123' },
    })
    fireEvent.click(screen.getByRole('button', { name: /pages\.personalCenter\.mfaRegenerateOk/ }))

    await waitFor(() => {
      expect(mfaService.regenerateRecoveryCodes).toHaveBeenCalledWith('admin123')
    })
    await waitFor(() => {
      expect(screen.getByText('ZZZZ2345')).toBeInTheDocument()
    })
    // 恢复码页仍然要"抄好了才能关"
    const done = screen.getByRole('button', { name: /pages\.personalCenter\.mfaRecoveryDone/ })
    expect(done).toBeDisabled()
    expect(within(screen.getByRole('checkbox').closest('label')!).getByText(
      'pages.personalCenter.mfaRecoveryAck',
    )).toBeInTheDocument()
  })

  it('弹窗关掉再打开时，上一次输入的动态码不会残留', async () => {
    vi.mocked(mfaService.fetchMfaStatus).mockResolvedValue(disabledStatus)
    vi.mocked(mfaService.setupMfa).mockResolvedValue({
      secret: 'JBSWY3DPEHPK3PXP',
      otpauthUrl: 'otpauth://totp/CRM:admin?secret=JBSWY3DPEHPK3PXP',
      qrCodeDataUrl: 'data:image/png;base64,AAAA',
      enabled: false,
    })
    await renderPage()
    await waitFor(() => {
      expect(screen.getByText('pages.personalCenter.mfaDisabled')).toBeInTheDocument()
    })

    fireEvent.click(actionButton('pages.personalCenter.btnEnableMfa'))
    const input = await screen.findByLabelText('pages.personalCenter.mfaCodeLabel')
    fireEvent.change(input, { target: { value: '999999' } })
    expect(input).toHaveValue('999999')

    // 取消（antd 的 Modal 在 destroyOnClose 下会连表单一起卸载）
    fireEvent.click(screen.getByRole('button', { name: /common\.button\.cancel/ }))
    fireEvent.click(actionButton('pages.personalCenter.btnEnableMfa'))

    // 一张新的二维码 ⇒ 一把新的密钥，上一次那个码对新密钥毫无意义；
    // 残留的旧值会让人以为"我已经输过了"。
    const reopened = await screen.findByLabelText('pages.personalCenter.mfaCodeLabel')
    expect(reopened).toHaveValue('')
  })
})
