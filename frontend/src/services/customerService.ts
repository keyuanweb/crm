import { apiClient, type PageResult } from './apiClient'
import type { Customer, CustomerDetail } from '../types/customer'

export interface CustomerPayload {
  name: string
  company: string
  contactPerson?: string
  phone?: string
  email?: string
  address?: string
  remark?: string
  status?: string
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
  })
  downloadBlob(resp.data as Blob, `customers-${new Date().toISOString().slice(0, 10)}.xlsx`)
}

export async function downloadTemplate(): Promise<void> {
  const resp = await apiClient.get('/customers/import-template', { responseType: 'blob' })
  downloadBlob(resp.data as Blob, 'customer-import-template.xlsx')
}
