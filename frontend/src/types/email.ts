/** 邮件营销（030）。 */
export interface EmailTemplate {
  id: number
  name: string
  subject: string
  content: string
  category: string
}

export interface EmailTemplatePayload {
  name: string
  subject: string
  content: string
  category: string
}

export interface EmailCampaign {
  id: number
  templateId: number
  name: string
  sourceType: string
  totalCount: number
  sentCount: number
  failedCount: number
  openCount: number
  clickCount: number
  status: string
  createdAt?: string
}

export interface EmailSendLog {
  id: number
  email: string
  subject: string
  status: string
  errorMessage?: string
  createdAt?: string
}
