import axios from 'axios'

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

export const apiClient = axios.create({ baseURL: API_BASE_URL })

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

/** 从 axios 错误中提取后端错误信息。 */
export function extractErrorMessage(error: unknown, fallback = '请求失败，请稍后重试'): string {
  if (axios.isAxiosError(error)) {
    const body = error.response?.data as { error?: ApiErrorBody } | undefined
    if (body?.error?.message) return body.error.message
    return error.message ?? fallback
  }
  return fallback
}
