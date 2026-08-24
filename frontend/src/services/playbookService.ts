import { apiClient, type PageResult } from './apiClient'
import type {
  ActionTemplatePayload,
  OpportunityAction,
  StageActionTemplate,
} from '../types/playbook'

export type { ActionTemplatePayload }

export async function fetchStageActions(
  stage?: string,
  page = 1,
  pageSize = 50,
): Promise<PageResult<StageActionTemplate>> {
  const { data } = await apiClient.get('/stage-actions', { params: { stage, page, pageSize } })
  return data.data as PageResult<StageActionTemplate>
}

export async function createStageAction(payload: ActionTemplatePayload): Promise<StageActionTemplate> {
  const { data } = await apiClient.post('/stage-actions', payload)
  return data.data as StageActionTemplate
}

export async function updateStageAction(id: number, payload: ActionTemplatePayload): Promise<StageActionTemplate> {
  const { data } = await apiClient.put(`/stage-actions/${id}`, payload)
  return data.data as StageActionTemplate
}

export async function deleteStageAction(id: number): Promise<void> {
  await apiClient.delete(`/stage-actions/${id}`)
}

export async function fetchOpportunityActions(soId: number): Promise<OpportunityAction[]> {
  const { data } = await apiClient.get(`/sales-opportunities/${soId}/actions`)
  return data.data as OpportunityAction[]
}

export async function completeOpportunityAction(soId: number, templateId: number): Promise<OpportunityAction> {
  const { data } = await apiClient.post(`/sales-opportunities/${soId}/actions`, { templateId })
  return data.data as OpportunityAction
}
