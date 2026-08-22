import { useQuery } from '@tanstack/react-query'
import {
  fetchCustomer,
  fetchCustomers,
  type CustomerListParams,
} from '../services/customerService'
import type { CustomerDetail } from '../types/customer'

/** 客户分页列表查询封装（T060：React Query 收敛到 hooks）。 */
export function useCustomerList(params: CustomerListParams) {
  return useQuery({
    queryKey: ['customers', params.keyword ?? '', params.status ?? '', params.page],
    queryFn: () => fetchCustomers(params),
  })
}

/** 客户详情查询封装（含商机与跟进时间线）。 */
export function useCustomerDetail(id: number | undefined) {
  return useQuery({
    queryKey: ['customer', id],
    queryFn: () => fetchCustomer(id as number),
    enabled: id !== undefined && Number.isFinite(id),
  })
}

export type { CustomerDetail }
