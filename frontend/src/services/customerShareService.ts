import { apiClient, type PageResult } from './apiClient'
import type { SharedCustomer } from '../types/customerShare'

export async function shareCustomer(customerId: number, sharedToUserId: number): Promise<SharedCustomer> {
  const { data } = await apiClient.post('/customer-shares', { customerId, sharedToUserId })
  return data.data as SharedCustomer
}

export async function unshareCustomer(shareId: number): Promise<void> {
  await apiClient.delete(`/customer-shares/${shareId}`)
}

export async function fetchSharedToMe(page: number, pageSize: number): Promise<PageResult<SharedCustomer>> {
  const { data } = await apiClient.get('/customer-shares/shared-to-me', { params: { page, pageSize } })
  return data.data as PageResult<SharedCustomer>
}
