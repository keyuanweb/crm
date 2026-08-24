import { apiClient, type PageResult } from './apiClient'
import type { FormField, FormPayload, OnlineForm, Submission } from '../types/form'

export async function fetchForms(): Promise<OnlineForm[]> {
  const { data } = await apiClient.get('/forms')
  return data.data as OnlineForm[]
}

export async function createForm(payload: FormPayload): Promise<OnlineForm> {
  const { data } = await apiClient.post('/forms', payload)
  return data.data as OnlineForm
}

export async function updateForm(id: number, payload: Partial<FormPayload>): Promise<OnlineForm> {
  const { data } = await apiClient.put(`/forms/${id}`, payload)
  return data.data as OnlineForm
}

export async function deleteForm(id: number): Promise<void> {
  await apiClient.delete(`/forms/${id}`)
}

export async function toggleForm(id: number): Promise<OnlineForm> {
  const { data } = await apiClient.post(`/forms/${id}/toggle`)
  return data.data as OnlineForm
}

export async function fetchSubmissions(
  id: number,
  params: { page?: number; pageSize?: number },
): Promise<PageResult<Submission>> {
  const { data } = await apiClient.get(`/forms/${id}/submissions`, { params })
  return data.data as PageResult<Submission>
}

/** 公开表单元信息（无需认证）。 */
export async function fetchPublicFormMeta(id: number): Promise<{
  name: string
  fields: FormField[]
  successMessage?: string
}> {
  const { data } = await apiClient.get(`/public/forms/${id}/meta`)
  return data.data as { name: string; fields: FormField[]; successMessage?: string }
}

/** 公开提交（无需认证）。 */
export async function submitPublicForm(id: number, payload: Record<string, string>): Promise<{
  submissionId: number
  leadId?: number
  message: string
}> {
  const { data } = await apiClient.post(`/public/forms/${id}/submit`, payload)
  return data.data as { submissionId: number; leadId?: number; message: string }
}
