import { apiClient, type PageResult } from './apiClient'
import type { Contact, ContactListParams, ContactPayload } from '../types/contact'
import type { ImportResult } from '../types/importResult'

export type { ContactListParams, ContactPayload }

export async function fetchContacts(params: ContactListParams): Promise<PageResult<Contact>> {
  const { data } = await apiClient.get('/contacts', { params })
  return data.data as PageResult<Contact>
}

export async function fetchContact(id: number): Promise<Contact> {
  const { data } = await apiClient.get(`/contacts/${id}`)
  return data.data as Contact
}

export async function createContact(payload: ContactPayload): Promise<Contact> {
  const { data } = await apiClient.post('/contacts', payload)
  return data.data as Contact
}

export async function updateContact(id: number, payload: ContactPayload): Promise<Contact> {
  const { data } = await apiClient.put(`/contacts/${id}`, payload)
  return data.data as Contact
}

export async function deleteContact(id: number): Promise<void> {
  await apiClient.delete(`/contacts/${id}`)
}

/** 批量导入联系人（024）。 */
export async function importContacts(file: File): Promise<ImportResult> {
  const form = new FormData()
  form.append('file', file)
  const { data } = await apiClient.post('/contacts/import', form)
  return data.data as ImportResult
}

/** 下载联系人导入模板（024）。 */
export async function downloadContactTemplate(): Promise<void> {
  const resp = await apiClient.get('/contacts/import-template', { responseType: 'blob' })
  const url = URL.createObjectURL(resp.data as Blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'contact-import-template.xlsx'
  a.click()
  URL.revokeObjectURL(url)
}
