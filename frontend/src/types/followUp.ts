export type FollowUpMethod = 'PHONE' | 'EMAIL' | 'MEETING' | 'OTHER'

export interface FollowUp {
  id: number
  customerId?: number
  leadId?: number
  opportunityId?: number
  method: FollowUpMethod
  content: string
  nextFollowUpAt?: string
  followUpBy?: number
  followUpByName?: string
  version: number
  createdAt: string
}

export interface FollowUpPayload {
  customerId?: number
  leadId?: number
  opportunityId?: number
  method: FollowUpMethod
  content: string
  nextFollowUpAt?: string
  createTask?: boolean
  version?: number
}
