import { apiClient, type PageResult } from './apiClient'
import type { Customer, CustomerDetail, CustomerHealthBrief } from '../types/customer'

export interface CustomerPayload {
  name: string
  company: string
  contactPerson?: string
  phone?: string
  email?: string
  address?: string
  remark?: string
  status?: string
  /** 营销活动归因（014）。 */
  campaignId?: number
  /** 自定义字段值（016）。 */
  customFieldValues?: { fieldId: number; value?: string }[]
  version?: number
}

export interface CustomerListParams {
  keyword?: string
  status?: string
  page: number
  pageSize: number
}

export async function fetchCustomers(params: CustomerListParams): Promise<PageResult<Customer>> {
  const { data } = await apiClient.get('/customers', { params })
  return data.data as PageResult<Customer>
}

export async function fetchCustomer(id: number): Promise<CustomerDetail> {
  const { data } = await apiClient.get(`/customers/${id}`)
  return data.data as CustomerDetail
}

/** 客户流失预警列表（018-customer-360）。 */
export async function fetchAtRiskCustomers(params: {
  daysInactive?: number
  page?: number
  pageSize?: number
}): Promise<PageResult<CustomerHealthBrief>> {
  const { data } = await apiClient.get('/customers/health/at-risk', { params })
  return data.data as PageResult<CustomerHealthBrief>
}

export async function createCustomer(payload: CustomerPayload): Promise<Customer> {
  const { data } = await apiClient.post('/customers', payload)
  return data.data as Customer
}

export async function updateCustomer(id: number, payload: CustomerPayload): Promise<Customer> {
  const { data } = await apiClient.put(`/customers/${id}`, payload)
  return data.data as Customer
}

export async function deleteCustomer(id: number): Promise<void> {
  await apiClient.delete(`/customers/${id}`)
}

// ===== 011 客户公海 =====

export async function fetchPoolCustomers(params: CustomerListParams): Promise<PageResult<Customer>> {
  const { data } = await apiClient.get('/customers/pool', { params })
  return data.data as PageResult<Customer>
}

export async function fetchMyCustomers(params: CustomerListParams): Promise<PageResult<Customer>> {
  const { data } = await apiClient.get('/customers/my', { params })
  return data.data as PageResult<Customer>
}

export async function claimCustomer(id: number): Promise<Customer> {
  const { data } = await apiClient.post(`/customers/pool/${id}/claim`)
  return data.data as Customer
}

export async function scanPool(): Promise<{ returnedCount: number }> {
  const { data } = await apiClient.post('/customers/pool/scan')
  return data.data as { returnedCount: number }
}

export async function batchTransferCustomers(
  customerIds: number[],
  targetOwnerId: number,
): Promise<number> {
  const { data } = await apiClient.post('/customers/batch-transfer', { customerIds, targetOwnerId })
  return data.data as number
}

export interface ImportResult {
  successCount: number
  failureCount: number
  failures: { row: number; message: string }[]
}

export async function importCustomers(file: File): Promise<ImportResult> {
  const formData = new FormData()
  formData.append('file', file)
  const { data } = await apiClient.post('/customers/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    // 大文件的传输 + 服务端同步解析：超时会把"其实导入成功了"报成失败，而用户重试就是重复导入
    // ⇒ 退出全局 30s 超时（见 `apiClient.REQUEST_TIMEOUT_MS`）。
    timeout: 0,
  })
  return data.data as ImportResult
}

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

export async function exportCustomers(params: { keyword?: string; status?: string }): Promise<void> {
  const resp = await apiClient.get('/customers/export', {
    params,
    responseType: 'blob',
    // 服务端同步生成整份 xlsx，耗时由客户数量决定 ⇒ 退出全局 30s 超时。
    timeout: 0,
  })
  downloadBlob(resp.data as Blob, `customers-${new Date().toISOString().slice(0, 10)}.xlsx`)
}

export async function downloadTemplate(): Promise<void> {
  const resp = await apiClient.get('/customers/import-template', { responseType: 'blob' })
  downloadBlob(resp.data as Blob, 'customer-import-template.xlsx')
}
