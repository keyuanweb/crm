export interface MailAccount {
  id: number
  email: string
  displayName: string
  imapHost?: string
  imapPort?: number
  smtpHost?: string
  smtpPort?: number
  enabled: boolean
  isDefaultSender: boolean
  createdAt?: string
}

export interface MailSyncRecord {
  id: number
  accountId: number
  direction: 'INBOUND' | 'OUTBOUND'
  subject?: string
  fromAddress?: string
  toAddress?: string
  /** SYNCED = 真实收信落库；FAILED = 失败；SIMULATED = 演示记录，**不是**真实收信（101）。 */
  syncStatus: 'SYNCED' | 'FAILED' | 'SIMULATED'
  externalId?: string
  syncTime?: string
}
