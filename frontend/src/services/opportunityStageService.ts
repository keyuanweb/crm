import { apiClient } from './apiClient'
import type { OpportunityStageDef, StageType } from '../types/opportunity'

export interface OpportunityStagePayload {
  /** 仅新建时提交；编辑时编码不可改（历史商机的 stage 列按它关联）。 */
  code?: string
  name: string
  sortOrder?: number
  probability?: number
  /** 仅新建时提交，且服务端强制 ACTIVE（不允许再建第二个「赢单」阶段）。 */
  stageType?: StageType
}

/** 阶段字典（含已停用），按 sortOrder 升序。读接口无需权限——看板列头与表单下拉都要它。 */
export async function fetchOpportunityStages(): Promise<OpportunityStageDef[]> {
  const { data } = await apiClient.get('/opportunity-stages')
  return data.data as OpportunityStageDef[]
}

export async function createOpportunityStage(
  payload: OpportunityStagePayload,
): Promise<OpportunityStageDef> {
  const { data } = await apiClient.post('/opportunity-stages', payload)
  return data.data as OpportunityStageDef
}

export async function updateOpportunityStage(
  id: number,
  payload: OpportunityStagePayload,
): Promise<OpportunityStageDef> {
  const { data } = await apiClient.put(`/opportunity-stages/${id}`, payload)
  return data.data as OpportunityStageDef
}

/** 启停。停用只禁止「新进入」：存量商机仍在该阶段里，仍可编辑、可移出。 */
export async function setOpportunityStageEnabled(id: number, enabled: boolean): Promise<void> {
  await apiClient.post(`/opportunity-stages/${id}/enabled`, null, { params: { enabled } })
}

export async function deleteOpportunityStage(id: number): Promise<void> {
  await apiClient.delete(`/opportunity-stages/${id}`)
}
