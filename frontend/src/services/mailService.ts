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

/**
 * 触发收信同步（062 的 `simulateSync` 改名）。
 *
 * 名称里不再有 "simulate"：本部署没有真实收信源时后端回 409 `MAIL_INBOUND_NOT_CONFIGURED`
 * 且**不写任何记录**；只有部署方显式打开演示开关时才返回一条 `syncStatus === 'SIMULATED'`
 * 的演示记录。旧名字会让人以为"点一下就有收信"，那正是 101 要关掉的那层含义。
 */
export async function triggerSync(accountId: number): Promise<MailSyncRecord> {
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
