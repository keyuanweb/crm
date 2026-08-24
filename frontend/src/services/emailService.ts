import { apiClient, type PageResult } from './apiClient'
import type { EmailCampaign, EmailSendLog, EmailTemplate, EmailTemplatePayload } from '../types/email'

export async function fetchEmailTemplates(category?: string): Promise<EmailTemplate[]> {
  const { data } = await apiClient.get('/email-templates', { params: { category } })
  return data.data as EmailTemplate[]
}

export async function createEmailTemplate(payload: EmailTemplatePayload): Promise<EmailTemplate> {
  const { data } = await apiClient.post('/email-templates', payload)
  return data.data as EmailTemplate
}

export async function updateEmailTemplate(
  id: number,
  payload: Partial<EmailTemplatePayload>,
): Promise<EmailTemplate> {
  const { data } = await apiClient.put(`/email-templates/${id}`, payload)
  return data.data as EmailTemplate
}

export async function deleteEmailTemplate(id: number): Promise<void> {
  await apiClient.delete(`/email-templates/${id}`)
}

export async function createEmailCampaign(payload: {
  name: string
  templateId: number
  sourceType: string
  segmentId?: number
  customerIds?: number[]
}): Promise<EmailCampaign> {
  const { data } = await apiClient.post('/email-campaigns', payload)
  return data.data as EmailCampaign
}

export async function testSendCampaign(id: number, email: string): Promise<void> {
  await apiClient.post(`/email-campaigns/${id}/test`, { email })
}

export async function fetchEmailCampaigns(): Promise<EmailCampaign[]> {
  const { data } = await apiClient.get('/email-campaigns')
  return data.data as EmailCampaign[]
}

export async function fetchCampaignDetail(
  id: number,
  params: { page?: number; pageSize?: number },
): Promise<PageResult<EmailSendLog>> {
  const { data } = await apiClient.get(`/email-campaigns/${id}`, { params })
  return data.data as PageResult<EmailSendLog>
}
