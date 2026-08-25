import { apiClient, type PageResult } from './apiClient'
import type { MailAccount, MailSyncRecord } from '../types/mail'

export async function fetchMailAccounts(): Promise<MailAccount[]> {
  const { data } = await apiClient.get('/mail-accounts')
  return data.data as MailAccount[]
}

export async function createMailAccount(payload: Partial<MailAccount> & { email: string; displayName: string }): Promise<MailAccount> {
  const { data } = await apiClient.post('/mail-accounts', payload)
  return data.data as MailAccount
}

export async function updateMailAccount(id: number, payload: Partial<MailAccount> & { email: string; displayName: string }): Promise<MailAccount> {
  const { data } = await apiClient.put(`/mail-accounts/${id}`, payload)
  return data.data as MailAccount
}

export async function deleteMailAccount(id: number): Promise<void> {
  await apiClient.delete(`/mail-accounts/${id}`)
}

export async function simulateSync(accountId: number): Promise<MailSyncRecord> {
  const { data } = await apiClient.post(`/mail-accounts/${accountId}/sync`)
  return data.data as MailSyncRecord
}

export async function fetchSyncRecords(
  accountId: number,
  page = 1,
  pageSize = 20,
): Promise<PageResult<MailSyncRecord>> {
  const { data } = await apiClient.get(`/mail-accounts/${accountId}/records`, {
    params: { page, pageSize },
  })
  return data.data as PageResult<MailSyncRecord>
}

export async function deleteSyncRecord(accountId: number, recordId: number): Promise<void> {
  await apiClient.delete(`/mail-accounts/${accountId}/records/${recordId}`)
}
