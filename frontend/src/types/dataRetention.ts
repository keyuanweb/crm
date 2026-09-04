/** 数据保留策略类型定义（080-data-retention）。 */

export type EntityType = 'CUSTOMER' | 'OPPORTUNITY' | 'CONTRACT' | 'ORDER' | 'AUDIT_LOG'
export type ActionType = 'ARCHIVE' | 'DELETE'
export type PolicyStatus = 'ACTIVE' | 'INACTIVE'
export type ExecutionStatus = 'SUCCESS' | 'FAILED' | 'PARTIAL'

export interface DataRetentionPolicyRequest {
  entityType: EntityType
  retentionDays: number
  actionType: ActionType
}

export interface DataRetentionPolicyResponse {
  id: number
  entityType: EntityType
  retentionDays: number
  actionType: ActionType
  status: PolicyStatus
  createdAt?: string
  updatedAt?: string
}

export interface DataRetentionExecutionResponse {
  id: number
  policyId: number
  executedAt: string
  status: ExecutionStatus
  processedCount?: number
  errorMessage?: string
  createdAt?: string
}

export const ENTITY_TYPE_LABELS: Record<EntityType, string> = {
  CUSTOMER: '客户',
  OPPORTUNITY: '商机',
  CONTRACT: '合同',
  ORDER: '订单',
  AUDIT_LOG: '审计日志',
}

export const ACTION_TYPE_LABELS: Record<ActionType, string> = {
  ARCHIVE: '归档',
  DELETE: '删除',
}

export const POLICY_STATUS_LABELS: Record<PolicyStatus, string> = {
  ACTIVE: '活跃',
  INACTIVE: '已停用',
}

export const EXECUTION_STATUS_LABELS: Record<ExecutionStatus, string> = {
  SUCCESS: '成功',
  FAILED: '失败',
  PARTIAL: '部分成功',
}
