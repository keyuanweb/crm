import { apiClient, type PageResult } from './apiClient'
import type {
  Contract,
  ContractListParams,
  ContractPayload,
  ContractTemplate,
  ContractTemplatePayload,
  RenewalGroup,
} from '../types/contract'

export type { ContractListParams, ContractPayload, ContractTemplatePayload, RenewalGroup }

export async function fetchContracts(params: ContractListParams): Promise<PageResult<Contract>> {
  const { data } = await apiClient.get('/contracts', { params })
  return data.data as PageResult<Contract>
}

export async function fetchContract(id: number): Promise<Contract> {
  const { data } = await apiClient.get(`/contracts/${id}`)
  return data.data as Contract
}

export async function createContract(payload: ContractPayload): Promise<Contract> {
  const { data } = await apiClient.post('/contracts', payload)
  return data.data as Contract
}

export async function updateContract(id: number, payload: ContractPayload): Promise<Contract> {
  const { data } = await apiClient.put(`/contracts/${id}`, payload)
  return data.data as Contract
}

export async function submitContract(id: number): Promise<Contract> {
  const { data } = await apiClient.post(`/contracts/${id}/submit`)
  return data.data as Contract
}

export async function approveContract(id: number): Promise<Contract> {
  const { data } = await apiClient.post(`/contracts/${id}/approve`)
  return data.data as Contract
}

export async function rejectContract(id: number, reason: string): Promise<Contract> {
  const { data } = await apiClient.post(`/contracts/${id}/reject`, { reason })
  return data.data as Contract
}

export async function effectiveContract(id: number): Promise<Contract> {
  const { data } = await apiClient.post(`/contracts/${id}/effective`)
  return data.data as Contract
}

export async function completeContract(id: number): Promise<Contract> {
  const { data } = await apiClient.post(`/contracts/${id}/complete`)
  return data.data as Contract
}

export async function terminateContract(id: number, reason: string): Promise<Contract> {
  const { data } = await apiClient.post(`/contracts/${id}/terminate`, { reason })
  return data.data as Contract
}

// ===== 附件 =====

export async function uploadContractAttachment(
  contractId: number,
  file: File,
): Promise<{ id: number; fileName: string }> {
  const formData = new FormData()
  formData.append('file', file)
  const { data } = await apiClient.post(`/contracts/${contractId}/attachments`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    // 附件大小不可控 ⇒ 退出全局 30s 超时（见 `apiClient.REQUEST_TIMEOUT_MS`）。
    timeout: 0,
  })
  return data.data as { id: number; fileName: string }
}

export async function downloadContractAttachment(contractId: number, attachmentId: number): Promise<void> {
  const resp = await apiClient.get(`/contracts/${contractId}/attachments/${attachmentId}/download`, {
    responseType: 'blob',
    // 附件大小不可控 ⇒ 退出全局 30s 超时（见 `apiClient.REQUEST_TIMEOUT_MS`）。
    timeout: 0,
  })
  const contentDisposition = resp.headers['content-disposition'] as string | undefined
  const match = contentDisposition?.match(/filename\*=UTF-8''(.+)/)
  const filename = match ? decodeURIComponent(match[1]) : `attachment-${attachmentId}`
  const url = URL.createObjectURL(resp.data as Blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

export async function deleteContractAttachment(contractId: number, attachmentId: number): Promise<void> {
  await apiClient.delete(`/contracts/${contractId}/attachments/${attachmentId}`)
}

// ===== 模板 =====

export async function fetchContractTemplates(params: {
  keyword?: string
  status?: string
  page: number
  pageSize: number
}): Promise<PageResult<ContractTemplate>> {
  const { data } = await apiClient.get('/contract-templates', { params })
  return data.data as PageResult<ContractTemplate>
}

export async function createContractTemplate(payload: ContractTemplatePayload): Promise<ContractTemplate> {
  const { data } = await apiClient.post('/contract-templates', payload)
  return data.data as ContractTemplate
}

export async function updateContractTemplate(
  id: number,
  payload: ContractTemplatePayload,
): Promise<ContractTemplate> {
  const { data } = await apiClient.put(`/contract-templates/${id}`, payload)
  return data.data as ContractTemplate
}

export async function deleteContractTemplate(id: number): Promise<void> {
  await apiClient.delete(`/contract-templates/${id}`)
}

// ===== 046 续约管理 =====

export async function fetchRenewalOverview(
  group: RenewalGroup,
  keyword?: string,
  page = 1,
  pageSize = 20,
): Promise<PageResult<Contract>> {
  const { data } = await apiClient.get('/contracts/renewal-overview', {
    params: { group, keyword, page, pageSize },
  })
  return data.data as PageResult<Contract>
}
