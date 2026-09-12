export type CallDirection = 'INBOUND' | 'OUTBOUND'
export type CallResult = 'CONNECTED' | 'NO_ANSWER' | 'BUSY' | 'FAILED'

export interface CallRecord {
  id: number
  customerId?: number
  customerName?: string
  contactId?: number
  contactName?: string
  direction: CallDirection
  durationSeconds: number
  result: CallResult
  remark?: string
  recordedBy?: number
  recordedAt?: string
}

export interface CallStats {
  totalCount: number
  totalDurationSeconds: number
  avgDurationSeconds: number
  byDirection: { direction: string; count: number }[]
}
