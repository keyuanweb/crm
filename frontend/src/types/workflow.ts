export type WorkflowEventType =
  | 'LEAD_CREATED'
  | 'OPPORTUNITY_STAGE_CHANGED'
  | 'FOLLOW_UP_CREATED'
  | 'PAYMENT_RECORDED'
  | 'LEAD_SCORE_THRESHOLD'
  | 'TAG_CHANGED'

export type WorkflowActionType = 'CREATE_TASK' | 'ASSIGN' | 'NOTIFY' | 'SEND_EMAIL' | 'ADD_TAG'

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
