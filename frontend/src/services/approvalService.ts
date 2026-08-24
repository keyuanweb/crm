import { apiClient } from './apiClient'
import type { ApprovalDetail, ApprovalFlow, ApprovalInstance, ApprovalTask, FlowNode } from '../types/approval'

export async function fetchApprovalFlows(businessType?: string): Promise<ApprovalFlow[]> {
  const { data } = await apiClient.get('/approval-flows', { params: { businessType } })
  return data.data as ApprovalFlow[]
}

export async function createApprovalFlow(payload: {
  name: string
  businessType: string
  nodes: FlowNode[]
  conditionJson?: { field: string; op: string; value: number; extraNodes: FlowNode[] }
  enabled: boolean
}): Promise<ApprovalFlow> {
  const { data } = await apiClient.post('/approval-flows', payload)
  return data.data as ApprovalFlow
}

export async function updateApprovalFlow(
  id: number,
  payload: Partial<{
    name: string
    businessType: string
    nodes: FlowNode[]
    conditionJson?: { field: string; op: string; value: number; extraNodes: FlowNode[] }
    enabled: boolean
  }>,
): Promise<ApprovalFlow> {
  const { data } = await apiClient.put(`/approval-flows/${id}`, payload)
  return data.data as ApprovalFlow
}

export async function deleteApprovalFlow(id: number): Promise<void> {
  await apiClient.delete(`/approval-flows/${id}`)
}

export async function fetchApprovalTodos(): Promise<ApprovalTask[]> {
  const { data } = await apiClient.get('/approvals/todos')
  return data.data as ApprovalTask[]
}

export async function fetchApprovalDone(): Promise<ApprovalTask[]> {
  const { data } = await apiClient.get('/approvals/done')
  return data.data as ApprovalTask[]
}

export async function fetchApprovalDetail(id: number): Promise<ApprovalDetail> {
  const { data } = await apiClient.get(`/approvals/${id}`)
  return data.data as ApprovalDetail
}

export async function approveTask(instanceId: number, taskId: number, comment?: string): Promise<void> {
  await apiClient.post(`/approvals/${instanceId}/tasks/${taskId}/approve`, { comment })
}

export async function rejectTask(instanceId: number, taskId: number, comment: string): Promise<void> {
  await apiClient.post(`/approvals/${instanceId}/tasks/${taskId}/reject`, { comment })
}

export async function transferTask(instanceId: number, taskId: number, toUserId: number): Promise<void> {
  await apiClient.post(`/approvals/${instanceId}/tasks/${taskId}/transfer`, { toUserId })
}

export async function renewApproval(instanceId: number): Promise<ApprovalInstance> {
  const { data } = await apiClient.post(`/approvals/${instanceId}/renew`)
  return data.data as ApprovalInstance
}

export async function fetchApprovalsByBusiness(
  businessType: string,
  businessId: number,
): Promise<ApprovalInstance[]> {
  const { data } = await apiClient.get(`/approvals/business/${businessType}/${businessId}`)
  return data.data as ApprovalInstance[]
}
