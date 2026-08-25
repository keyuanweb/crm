export interface ApiKey {
  id: number
  name: string
  key?: string
  keyPrefix?: string
  scopes?: string[]
  expiresAt?: string
  status: string
  lastUsedAt?: string
  useCount?: number
  createdAt?: string
}

export interface WebhookSubscription {
  id: number
  eventType: string
  callbackUrl: string
  secret?: string
  enabled: boolean
  createdAt?: string
}

export interface WebhookDelivery {
  id: number
  subscriptionId: number
  eventType: string
  entityType?: string
  entityId?: number
  status: string
  httpStatus?: number
  error?: string
  retryCount?: number
  createdAt: string
}

export const WEBHOOK_EVENT_LABELS: Record<string, string> = {
  LEAD_CREATED: '线索创建',
  LEAD_UPDATED: '线索更新',
  CUSTOMER_CREATED: '客户创建',
}
