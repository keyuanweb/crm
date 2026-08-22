export type WorkflowEventType =
  | 'LEAD_CREATED'
  | 'OPPORTUNITY_STAGE_CHANGED'
  | 'FOLLOW_UP_CREATED'
  | 'PAYMENT_RECORDED'

export type WorkflowActionType = 'CREATE_TASK' | 'ASSIGN' | 'NOTIFY'

export interface WorkflowRule {
  id: number
  name: string
  eventType: WorkflowEventType
  condition?: { field: string; value: string }
  actionType: WorkflowActionType
  action: Record<string, unknown>
  enabled: boolean
  version: number
  createdAt?: string
}

export interface WorkflowRulePayload {
  name: string
  eventType: string
  condition?: { field: string; value: string }
  actionType: string
  action: Record<string, unknown>
  enabled?: boolean
  version?: number
}

export interface ExecutionLog {
  id: number
  ruleId: number
  ruleName?: string
  eventType: string
  entityType?: string
  entityId?: number
  matched: boolean
  actionResult?: string
  success: boolean
  errorMessage?: string
  createdAt: string
}

export interface Notification {
  id: number
  message: string
  read: boolean
  createdAt: string
}

export const EVENT_LABELS: Record<WorkflowEventType, string> = {
  LEAD_CREATED: '线索创建',
  OPPORTUNITY_STAGE_CHANGED: '商机阶段变更',
  FOLLOW_UP_CREATED: '跟进创建',
  PAYMENT_RECORDED: '回款登记',
}

export const ACTION_LABELS: Record<WorkflowActionType, string> = {
  CREATE_TASK: '创建任务',
  ASSIGN: '自动分配',
  NOTIFY: '站内通知',
}
