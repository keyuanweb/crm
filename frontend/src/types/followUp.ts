export type FollowUpMethod = 'PHONE' | 'EMAIL' | 'MEETING' | 'OTHER'

export interface FollowUp {
  id: number
  customerId: number
  opportunityId?: number
  method: FollowUpMethod
  content: string
  nextFollowUpAt?: string
  followUpBy?: number
  followUpByName?: string
  version: number
  createdAt: string
}

export const METHOD_LABELS: Record<FollowUpMethod, string> = {
  PHONE: '电话',
  EMAIL: '邮件',
  MEETING: '面谈',
  OTHER: '其他',
}
