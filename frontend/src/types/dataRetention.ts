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
