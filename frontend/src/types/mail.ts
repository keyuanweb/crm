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
  syncStatus: 'SYNCED' | 'FAILED'
  externalId?: string
  syncTime?: string
}

export const SYNC_DIRECTION_LABELS: Record<string, string> = {
  INBOUND: '收件',
  OUTBOUND: '发件',
}
