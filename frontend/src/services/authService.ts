import { apiClient } from './apiClient'
import type { UserInfo } from '../store/authStore'

/**
 * 登录 / 二次验证的返回（082）。
 *
 * <p><b>三个令牌字段全部可选，这是本批唯一一处「放宽类型」，且不能收紧</b>：密码阶段的响应
 * 是**两选一**的——账号未启用 2FA 时返回 `{accessToken, refreshToken, user}`（与 082 之前
 * 逐字节相同），启用时只返回 `{mfaRequired, mfaToken, expiresIn}`，**不含任何令牌**。
 *
 * <p>改造前 `accessToken: string` 是非可选的，而 `LoginPage.onFinish` 无条件读它。若后端
 * 返回 `mfaRequired` 而前端不改，`setTokens(undefined, undefined)` 会把字符串 `"undefined"`
 * 写进 localStorage ⇒ `isAuthenticated()` 恒真 ⇒ 外壳渲染 ⇒ `fetchMe()` 401 ⇒ 401 拦截器
 * 硬跳 `/login` ⇒ **静默重定向环**。⇒ 前后端**不可独立发布**，这三个 `?` 就是那件事的类型留痕。
 */
export interface AuthResponse {
  accessToken?: string
  refreshToken?: string
  user?: UserInfo
  /** 该账号需要二次验证；此时只有 `mfaToken`，没有任何令牌。 */
  mfaRequired?: boolean
  /** 一次性二次验证票据（Redis 里 TTL 300 秒，用掉即失效）。**只留在组件内 state，不进 localStorage**。 */
  mfaToken?: string
  /** `mfaToken` 的剩余有效秒数。只在 `mfaRequired` 为真时出现。 */
  expiresIn?: number
}

/**
 * 收窄成「确定拿到了令牌」的那一支。
 *
 * <p>调用方需要 `accessToken` 是非空 string（`setTokens` 的形参就是这么要求的），
 * 而 `AuthResponse` 上它是可选的。用守卫而不是 `!`：票据缺失是**可以真的发生**的
 * （后端改坏了、或将来某支返回了新形状），而 `!` 会把它变成 localStorage 里的
 * 字符串 `"undefined"`——一个 HTTP 层完全看不出来的故障。
 */
export function hasTokens(
  res: AuthResponse,
): res is AuthResponse & { accessToken: string; refreshToken: string } {
  return typeof res.accessToken === 'string' && res.accessToken.length > 0
}

/** 收窄成「需要二次验证」的那一支。 */
export function isMfaChallenge(
  res: AuthResponse,
): res is AuthResponse & { mfaToken: string; expiresIn: number } {
  return res.mfaRequired === true && typeof res.mfaToken === 'string' && res.mfaToken.length > 0
}

export interface CaptchaResponse {
  captchaId: string
  imageBase64: string
}

export async function fetchCaptcha(): Promise<CaptchaResponse> {
  const { data } = await apiClient.get('/auth/captcha')
  return data.data as CaptchaResponse
}

export async function login(
  username: string,
  password: string,
  captchaId?: string,
  captchaCode?: string,
): Promise<AuthResponse> {
  const { data } = await apiClient.post('/auth/login', {
    username,
    password,
    captchaId,
    captchaCode,
  })
  return data.data as AuthResponse
}

/**
 * 提交第二因素，成功则拿到与单因素登录**同一形状**的完整结果。
 *
 * <p>本请求**不带 `Authorization`**：这一步的用户还没有令牌（票据是他在密码阶段唯一的凭证），
 * 后端也已把这条精确路径放行。若这里被加上令牌，端点行为不会变，但会掩盖
 * "这条路径本该是匿名的"这件事——将来有人收紧 `SecurityConfig` 时，症状会是
 * "登录页在二次验证那一步卡住"，而不是一条明确的 401。
 *
 * <p>`code` 与 `recoveryCode` **二选一**：两种凭据走的是同一条服务端分支选择，
 * 同时传时后端按恢复码优先（与 `MfaService.requireSecondFactor` 同一套语义）。
 */
export async function verifyMfa(
  mfaToken: string,
  payload: { code?: string; recoveryCode?: string },
): Promise<AuthResponse> {
  const { data } = await apiClient.post('/auth/2fa/verify', { mfaToken, ...payload })
  return data.data as AuthResponse
}

export async function fetchMe(): Promise<UserInfo> {
  const { data } = await apiClient.get('/auth/me')
  return data.data as UserInfo
}

export async function logout(refreshToken: string): Promise<void> {
  await apiClient.post('/auth/logout', { refreshToken })
}
