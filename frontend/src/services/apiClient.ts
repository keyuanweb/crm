import axios from 'axios'
import i18n from '../i18n'

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'

export interface ApiErrorBody {
  code?: string
  message?: string
  fieldErrors?: { field: string; message: string }[]
}

/** 后端统一信封 { success, data, error } */
export interface ApiEnvelope<T> {
  success: boolean
  data: T
  error?: ApiErrorBody
}

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
}

/**
 * 全局请求超时（毫秒）。
 *
 * <p>为什么要有它：axios 的默认值是 `0`，意思是**永不超时**。2026-09-13 排查「渠道 ROI 页面
 * 一直转圈、出不来数」时，后端进程楔住——TCP 连接建得起来、响应一个字节都不回（实测 6s/75s 皆然）。
 * 此时页面的 loading 永远不结束：用户只看到转圈，既没有报错，也无从判断是"慢"还是"死"。
 * 有了超时，同一种故障会变成一条明确的错误提示。
 *
 * <p>**纯文件传输不受它约束**：下载导出件/报表/附件/PDF、上传导入件的耗时由**文件大小**决定，
 * 且服务端可能已经在干活——超时会把"其实成功了"报成失败（导入尤其危险：用户重试即重复导入）。
 * 这些调用点各自显式传 `timeout: 0` 退出全局限制，见各处注释。
 */
export const REQUEST_TIMEOUT_MS = 30_000

export const apiClient = axios.create({ baseURL: API_BASE_URL, timeout: REQUEST_TIMEOUT_MS })

apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

apiClient.interceptors.response.use(
  (resp) => resp,
  (error) => {
    if (error.response?.status === 401 && window.location.pathname !== '/login') {
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)

/**
 * 是否为乐观锁冲突（后端 `VERSION_CONFLICT`，HTTP 409）。
 *
 * <p>调用方要单独识别它：409 的含义是「你手里的数据已经过期」，正确的处理是回滚本地改动并重新拉取，
 * 而不是把服务端的文案原样弹出来（用户看到「数据已被他人修改」也不知道下一步该做什么）。
 */
export function isVersionConflict(error: unknown): boolean {
  return axios.isAxiosError(error) && error.response?.status === 409
}

/**
 * 从 axios 错误中提取后端错误信息。
 *
 * <p>`error.response` 不存在时（超时、连不上、被 CORS 挡下）**一律回落到调用方文案**：
 * 这些情况下 axios 自己的 message 是英文的（`timeout of 30000ms exceeded` / `Network Error`），
 * 那是给开发者看的，不是给用户看的。后端的文案仍然优先——有响应体就说明拿到的是真结论。
 *
 * <p>**098：兜底文案改走 i18next 单例。** 默认实参原本是裸中文 `'请求失败，请稍后重试'`，而它会被
 * 原样弹给用户。本模块**不是组件、拿不到 `useTranslation` 的 hook** ⇒ 用 `i18n.t(...)`
 * （服务层取词的第一个先例，边界见 `services/visitService.ts#getCurrentPosition()` 的注释）。
 * <p>**语义与原来一致，且只强不强**：原默认实参在**每次调用时**求值，改为函数体内 `fallback ?? t(...)`
 * 同样在调用时求值 ⇒ 语言切换后拿到的是**当前语言**；调用方显式传 `''` 依旧被当作有效值（不是缺省）。
 * 唯一的差别是**翻译只在实际用到时才求值**（原来编译期常量，无所谓）。
 */
export function extractErrorMessage(error: unknown, fallback?: string): string {
  const fb = fallback ?? i18n.t('common.message.requestFailed')
  if (axios.isAxiosError(error)) {
    const body = error.response?.data as { error?: ApiErrorBody } | undefined
    if (body?.error?.message) return body.error.message
    if (!error.response) return fb
    return error.message ?? fb
  }
  return fb
}

/**
 * 从 axios 错误中提取后端错误码（`error.code`）。
 *
 * <p>为什么光有 {@link extractErrorMessage} 不够：**文案是给人看的，码是给分支用的**。同一批
 * 401 里，"动态码错了，还剩 3 次"和"这张票据已经作废"对用户是两件完全不同的事——前者该留在
 * 原地再输一次，后者留在原地**永远不可能成功**（票据是一次性的，过期或被消费之后，输对码也
 * 只会继续 401）。靠比对中文文案来区分这两者，等于把提示语的措辞变成控制流；后端改一个字，
 * 用户就卡死在一个死循环里。{@code ErrorCode} 里的字符串是接口契约的一部分，改它要动契约。
 *
 * <p>拿不到码时返回 `undefined`（超时、断网、CORS 都属此类）——调用方应把它当作"未知失败"，
 * 走通用的错误提示，而不是假定成某一个具体的码。
 */
export function extractErrorCode(error: unknown): string | undefined {
  if (axios.isAxiosError(error)) {
    const body = error.response?.data as { error?: ApiErrorBody } | undefined
    return body?.error?.code
  }
  return undefined
}
