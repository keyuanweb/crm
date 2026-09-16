import { apiClient } from './apiClient'

/**
 * 双因素认证（082）——**账号自己的**安全配置，与登录路径上的二次验证分开。
 *
 * <p>登录那一步（`authService.verifyMfa`）在这条边界之外：它跑在**未认证**的路径上、凭一张
 * 一次性票据，而本文件这五个端点全部要求已登录 JWT，且前四个改的是"这个账号往后怎么登录"。
 * 两者的错误处理、重试语义与可观测后果都不同，故不混在一个服务里。
 *
 * <p>⚠️ <b>本文件里的 `secret` 与 `recoveryCodes` 是明文凭证，只在各自的那一次响应里出现</b>
 * （落库只有 AES-256-GCM 密文与加盐哈希）。调用方拿到之后**不得**把它们写进 localStorage、
 * 日志、错误上报或任何持久化的地方——它们的存在时长应当与"用户抄下来"这件事一样短。
 */

/** `setup` 的返回：绑定所需的全部材料。`secret` 只此一次明文返回。 */
export interface MfaSetupResult {
  /** Base32 密钥，供手动输入认证器使用。 */
  secret: string
  /** 供二维码编码的 otpauth URL（label 与 issuer 已 URL 编码）。 */
  otpauthUrl: string
  /** `data:image/png;base64,...`，可直接喂给 `<img src>`。 */
  qrCodeDataUrl: string
  /** 恒为 `false`——setup 只是生成待绑定密钥，启用要等 `enable` 验码通过。 */
  enabled: boolean
}

/** `enable` 的返回：一组**只显示这一次**的恢复码。 */
export interface MfaEnableResult {
  enabled: boolean
  enabledAt?: string
  recoveryCodes: string[]
}

/** `status` 的返回：不泄漏任何凭证，只有"状态 + 还剩几个恢复码"。 */
export interface MfaStatus {
  enabled: boolean
  enabledAt?: string
  recoveryCodesRemaining: number
}

/**
 * 生成待绑定密钥与二维码（FR-M03/FR-M04）。
 *
 * <p>重复调用会**生成新密钥并使上一未完成密钥失效**（契约 §1）——所以"刷新二维码"这个动作
 * 会让用户**之前扫过的那张码作废**。界面上要把它说明白，否则用户会以为扫码失败是 App 的问题。
 *
 * @param password 可选的密码二次确认；不传则后端不校验密码
 */
export async function setupMfa(password?: string): Promise<MfaSetupResult> {
  // 后端把这个请求体声明成**可选**的（`@RequestBody(required = false)`），故这里永远送一个对象，
  // 不送 `undefined`：后者会让 axios 连 `{}` 都不发，虽然同样能过，但"发什么"取决于 axios 的
  // 空 body 处理，不如显式。
  const { data } = await apiClient.post('/auth/2fa/setup', password ? { password } : {})
  return data.data as MfaSetupResult
}

/**
 * 用一次动态码确认启用，返回恢复码。
 *
 * <p>这里的码错是 `MFA_CODE_INVALID`（含剩余尝试次数），与登录路径共用同一套失败计数与锁定
 * ——因此**在启用向导里连续输错 5 次会把该账号锁 15 分钟**，用户此时连登录都进不来。
 */
export async function enableMfa(code: string): Promise<MfaEnableResult> {
  const { data } = await apiClient.post('/auth/2fa/enable', { code })
  return data.data as MfaEnableResult
}

/** 查询当前账号的 2FA 状态（用于安全卡渲染）。 */
export async function fetchMfaStatus(): Promise<MfaStatus> {
  const { data } = await apiClient.get('/auth/2fa/status')
  return data.data as MfaStatus
}

/**
 * 重新生成恢复码：**旧的全部作废**（FR-M06）。
 *
 * <p>作废发生在响应返回**之前**（服务端先 `revokeAll` 再生成），所以"点了按钮但没记下新码"
 * 会让用户手里的旧码也一起失效——界面上必须是一次不可取消的确认，且新码要留在屏幕上
 * 直到用户明确关闭。
 */
export async function regenerateRecoveryCodes(password: string): Promise<string[]> {
  const { data } = await apiClient.post('/auth/2fa/recovery-codes/regenerate', { password })
  return (data.data as { recoveryCodes: string[] }).recoveryCodes
}

/**
 * 关闭 2FA（FR-M05）。
 *
 * <p>要**密码 + 第二因素**两样：只凭密码就允许关掉，等于把"二次验证"降级成"知道密码即可
 * 绕过二次验证"。`code` 与 `recoveryCode` 二选一（恢复码是给"手机丢了"的人用的逃生通道）。
 */
export async function disableMfa(payload: {
  password: string
  code?: string
  recoveryCode?: string
}): Promise<void> {
  await apiClient.post('/auth/2fa/disable', payload)
}
