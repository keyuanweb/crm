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

/** 从 axios 错误中提取后端错误信息。 */
export function extractErrorMessage(error: unknown, fallback = '请求失败，请稍后重试'): string {
  if (axios.isAxiosError(error)) {
    const body = error.response?.data as { error?: ApiErrorBody } | undefined
    if (body?.error?.message) return body.error.message
    return error.message ?? fallback
  }
  return fallback
}
