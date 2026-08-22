import { apiClient, type PageResult } from './apiClient'
import type { ExecutionLog, Notification, WorkflowRule, WorkflowRulePayload } from '../types/workflow'

export type { WorkflowRulePayload }

export async function fetchWorkflowRules(params: {
  keyword?: string
  eventType?: string
  enabled?: boolean
  page: number
  pageSize: number
}): Promise<PageResult<WorkflowRule>> {
  const { data } = await apiClient.get('/workflows/rules', { params })
  return data.data as PageResult<WorkflowRule>
}

export async function createWorkflowRule(payload: WorkflowRulePayload): Promise<WorkflowRule> {
  const { data } = await apiClient.post('/workflows/rules', payload)
  return data.data as WorkflowRule
}

export async function updateWorkflowRule(id: number, payload: WorkflowRulePayload): Promise<WorkflowRule> {
  const { data } = await apiClient.put(`/workflows/rules/${id}`, payload)
  return data.data as WorkflowRule
}

export async function toggleWorkflowRule(id: number): Promise<WorkflowRule> {
  const { data } = await apiClient.post(`/workflows/rules/${id}/toggle`)
  return data.data as WorkflowRule
}

export async function deleteWorkflowRule(id: number): Promise<void> {
  await apiClient.delete(`/workflows/rules/${id}`)
}

export async function fetchWorkflowLogs(params: {
  ruleId?: number
  eventType?: string
  success?: boolean
  page: number
  pageSize: number
}): Promise<PageResult<ExecutionLog>> {
  const { data } = await apiClient.get('/workflows/logs', { params })
  return data.data as PageResult<ExecutionLog>
}

export async function fetchNotifications(page: number, pageSize: number): Promise<PageResult<Notification>> {
  const { data } = await apiClient.get('/workflows/notifications', { params: { page, pageSize } })
  return data.data as PageResult<Notification>
}

export async function markNotificationRead(id: number): Promise<void> {
  await apiClient.post(`/workflows/notifications/${id}/read`)
}
