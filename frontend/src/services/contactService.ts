import { apiClient, type PageResult } from './apiClient'
import type { Contact, ContactListParams, ContactPayload } from '../types/contact'

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
